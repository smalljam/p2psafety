package ua.p2psafety.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;

import ua.p2psafety.data.Prefs;
import ua.p2psafety.json.Event;
import ua.p2psafety.json.Role;
import ua.p2psafety.json.User;

import static ua.p2psafety.util.Utils.errorDialog;

public class NetworkManager {
    public static final int SITE = 0;
    public static final int FACEBOOK = 1;

    private static final String SERVER_URL = "https://p2psafety.net";
    public static Logs LOGS;

    private static final int CODE_SUCCESS = 201;

    private static HttpClient httpClient;
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static ObjectMapper mapper = new ObjectMapper();

    public static void init(Context context) {
        HttpParams httpParams = new BasicHttpParams();
        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        HttpConnectionParams.setConnectionTimeout(httpParams, 0);
        HttpConnectionParams.setSoTimeout(httpParams, 0);

        // https
        HostnameVerifier hostnameVerifier = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
        SchemeRegistry schReg = new SchemeRegistry();
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        socketFactory.setHostnameVerifier((X509HostnameVerifier) hostnameVerifier);
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", socketFactory, 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(httpParams, schReg);

        httpClient = new DefaultHttpClient(conMgr, httpParams);
        HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);

        LOGS = new Logs(context);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        if (LOGS != null)
            LOGS.close();
    }

    public static void createEvent(final Context context) {
        createEvent(context, new DeliverResultRunnable());
    }

    public static void createEvent(final Context context,
                                   final DeliverResultRunnable<Event> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "createEvent";
                LOGS.info("EventManager. CreateEvent.");
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        LOGS.info("EventManager. createEvent. No network.");
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    LOGS.info("EventManager. CreateEvent. Request: " + httpPost.getRequestLine().toString());
                    LOGS.info("EventManager. CreateEvent. Request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    LOGS.info("EventManager. CreateEvent. ResponseCode: " + responseCode);
                    LOGS.info("EventManager. CreateEvent. ResponseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        LOGS.info("EventManager. CreateEvent. Success");
                        postRunnable.setResult(event);
                    } else {
                        LOGS.info("EventManager. CreateEvent. Failure");
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create event", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void updateEventWithAttachment(Context context, File file, boolean isAudio) {
        updateEventWithAttachment(context, file, isAudio, new DeliverResultRunnable());
    }

//    public static void createEventSupport(final Context context, final String id, final String userId,
//                                   final DeliverResultRunnable<Boolean> postRunnable) {
//        executor.execute(new Runnable() {
//            @Override
//            public void run() {
//                final String TAG = "createEventSupport";
//
//                if (!Utils.isNetworkConnected(context, LOGS)) {
////                    errorDialog(context, DIALOG_NO_CONNECTION);
//                    if (postRunnable != null) {
//                        postRunnable.setResult(null);
//                        postRunnable.run();
//                    }
//                    return;
//                }
//
//                String access_token = Session.getActiveSession().getAccessToken();
//
//                try {
//                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
//                            .append("/api/v1/events/").append(id).append("/support/").toString());
//
//                    addAuthHeader(context, httpPost);
//                    addUserAgentHeader(context, httpPost);
//                    httpPost.setHeader("Accept", "application/json");
//                    httpPost.setHeader("Content-type", "application/json");
//
//                    JSONObject json = new JSONObject();
//                    json.put("user_id", userId);
//                    StringEntity se = new StringEntity(json.toString());
//                    httpPost.setEntity(se);
//
//                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
//                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));
//
//                    HttpResponse response = null;
//                    try {
//                        response = httpClient.execute(httpPost);
//                    } catch (Exception e) {
//                        NetworkManager.LOGS.error("Can't execute post request", e);
//                        //errorDialog(context, DIALOG_NETWORK_ERROR);
//                        if (postRunnable != null) {
//                            postRunnable.setResult(null);
//                            postRunnable.run();
//                        }
//                        return;
//                    }
//
//                    int responseCode = response.getStatusLine().getStatusCode();
//                    String responseContent = EntityUtils.toString(response.getEntity());
//                    Log.i(TAG, "responseCode: " + responseCode);
//                    Log.i(TAG, "responseContent: " + responseContent);
//
//                    if (responseCode == CODE_SUCCESS) {
//                        postRunnable.setResult(true);
//                    } else {
//                        postRunnable.setResult(false);
//                    }
//
//                    if (postRunnable != null) {
//                        postRunnable.run();
//                    }
//                } catch (Exception e) {
//                    NetworkManager.LOGS.error("Can't create event", e);
//                    //errorDialog(context, DIALOG_NETWORK_ERROR);
//                    if (postRunnable != null) {
//                        postRunnable.setResult(null);
//                        postRunnable.run();
//                    }
//                }
//            }
//        });
//    }

    public static void getInfoAboutEvent(final Context context, final String id,
                                          final DeliverResultRunnable<Event> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "getInfoAboutEvent";
                final int CODE_SUCCESS = 200;

                if (!Utils.isNetworkConnected(context, LOGS)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                    return;
                }

                try {
                    HttpGet httpGet = new HttpGet(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/").append(id).append("/").toString());

                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        //errorDialog(context, DIALOG_NETWORK_ERROR);
                        if (postRunnable != null) {
                            postRunnable.setResult(null);
                            postRunnable.run();
                        }
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        LOGS.info("NetworkManager. GetInfoAboutEvent. Success");
                        postRunnable.setResult(event);
                    } else {
                        LOGS.info("NetworkManager. GetInfoAboutEvent. Failure");
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get info about event", e);
                    //errorDialog(context, DIALOG_NETWORK_ERROR);
                    if (postRunnable != null) {
                        postRunnable.setResult(null);
                        postRunnable.run();
                    }
                }
            }
        });
    }

    public static void updateEventWithAttachment(final Context context,
                                   final File file, final boolean isAudio,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    Event event = EventManager.getInstance(context).getEvent();

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);

                    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
                    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
                    if (isAudio)
                        builder.addPart("audio", new FileBody(file));
                    else
                        builder.addPart("video", new FileBody(file));

                    builder.addTextBody("key", event.getKey(), ContentType.APPLICATION_JSON);

                    httpPost.setEntity(builder.build());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't update event with attachments", e);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                }
            }
        });
    }

    public static void updateEvent(Context context, Map data) {
        updateEvent(context, data, new DeliverResultRunnable());
    }

    public static void updateEvent(final Context context,
                                   final Map data,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "updateEvent";
                LOGS.info("EventManager. UpdateEvent.");
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        LOGS.info("EventManager. UpdateEvent. No network");
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    Event event = EventManager.getInstance(context).getEvent();

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/eventupdates/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    json.put("key", event.getKey());
                    json.put("text", data.get("text"));
                    try {
                        Location loc = (Location) data.get("loc");
                        JSONObject jsonLocation = new JSONObject();
                        jsonLocation.put("latitude", loc.getLatitude());
                        jsonLocation.put("longitude", loc.getLongitude());
                        json.put("location", jsonLocation);
                    } catch (Exception e) {
                        LOGS.info("SosManager. UpdateEvent. Location is null. OK");
                        //for emulator testing
//                        Random rand = new Random();
//                        JSONObject jsonLocation = new JSONObject();
//                        jsonLocation.put("latitude", rand.nextInt(100));
//                        jsonLocation.put("longitude", rand.nextInt(100));
//                        json.put("location", jsonLocation);
                    }

                    StringEntity se = new StringEntity(json.toString(), "UTF-8");
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    LOGS.info("EventManager. UpdateEvent. Request: " + httpPost.getRequestLine().toString());
                    LOGS.info("EventManager. UpdateEvent. Request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        LOGS.info("EventManager. UpdateEvent. Executing request");
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    LOGS.info("EventManager. UpdateEvent. ResponseCode: " + responseCode);
                    LOGS.info("EventManager. UpdateEvent. ResponseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        LOGS.info("EventManager. UpdateEvent. Success");
                        postRunnable.setResult(true);
                    } else {
                        LOGS.info("EventManager. UpdateEvent. Failure");
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't update event", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void supportEvent(final Context context, final String support_url,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "supportEvent";
                final int CODE_SUCCESS = 200;

                if (!Utils.isNetworkConnected(context, LOGS)) {
//                    errorDialog(context, DIALOG_NO_CONNECTION);
                    if (postRunnable != null) {
                        postRunnable.setResult(false);
                        postRunnable.run();
                    }
                    return;
                }

                try {
                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append(support_url).toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONObject json = new JSONObject();
                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);
                    //httpPost.getParams().setParameter("user_id", EventManager.getInstance(context).getEvent().getUser().getId());

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        //errorDialog(context, DIALOG_NETWORK_ERROR);
                        if (postRunnable != null) {
                            postRunnable.setResult(false);
                            postRunnable.run();
                        }
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    if (postRunnable != null) {
                        postRunnable.run();
                    }
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create event", e);
                    //errorDialog(context, DIALOG_NETWORK_ERROR);
                    if (postRunnable != null) {
                        postRunnable.setResult(false);
                        postRunnable.run();
                    }
                }
            }
        });
    }

    public static void getEventUpdates(final Context context, final String event_id,
                                final DeliverResultRunnable<List<Event>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getEventUpdates";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("eventupdates?event=").append(event_id);

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        List<Event> result = JsonHelper.jsonResponseToEvents(responseContent);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getEvent(final Context context, final String event_id,
                                final DeliverResultRunnable<Event> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getEvent";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("events");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event result = JsonHelper.jsonToEvent(data);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getUserByEvent(final Context context, final String event_id,
                                final DeliverResultRunnable<User> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getUser";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("users?event__in=").append(event_id);

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        ObjectMapper mapper = new ObjectMapper();
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        User result = JsonHelper.jsonToUser(data);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    // TODO: make it work (now it returns code 401)
    public static void getEvents(final Context context,
                                 final DeliverResultRunnable<List<Event>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "getEvents";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpGet httpGet = new HttpGet(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/events/?format=json")
//                            .append("?user=")
//                            .append(EventManager.getInstance(context).getEvent().getUser().getId())
                            .toString());

                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    //httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        return;
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(null);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get events", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getRoles(final Context context,
                                final DeliverResultRunnable<List<Role>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("roles/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        List<Role> result = JsonHelper.jsonResponseToRoles(responseContent);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getMovementTypes(final Context context,
                                final DeliverResultRunnable<List<Role>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getMovementTypes";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/")
                            .append("movement_types/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity(), "UTF-8");
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        List<Role> result = JsonHelper.jsonResponseToRoles(responseContent);

                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getUserRoles(final Context context,
                                final DeliverResultRunnable<List<String>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getUserRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/users/roles/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw  new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        String[] roles = responseContent.substring(1, responseContent.length()-1)
                                                        .split(",");
                        List<String> result = Arrays.asList(roles);
                        Log.i(TAG, "result: " + String.valueOf(result));
                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void getUserMovementTypes(final Context context,
                                    final DeliverResultRunnable<List<String>> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "getUserMovementTypes";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder()
                            .append(SERVER_URL).append("/api/v1/users/movement_types/");

                    HttpGet httpGet = new HttpGet(url.toString());
                    addAuthHeader(context, httpGet);
                    addUserAgentHeader(context, httpGet);
                    httpGet.setHeader("Accept", "application/json");
                    httpGet.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpGet.getRequestLine().toString());

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpGet);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute get request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw  new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        String[] roles = responseContent.substring(1, responseContent.length()-1)
                                .split(",");
                        List<String> result = Arrays.asList(roles);
                        Log.i(TAG, "result: " + String.valueOf(result));
                        postRunnable.setResult(result);
                    } else {
                        postRunnable.setResult(null);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't get roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(null);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void setRoles(final Context context, User user, final List<Role> roles,
                                   final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "setRoles";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/users/roles/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONArray arr = new JSONArray();
                    for (Role role : roles)
                        if (role.checked)
                            arr.put(role.id);

                    JSONObject json = new JSONObject();
                    json.put("role_ids", arr);

                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void setMovementTypes(final Context context, User user, final List<Role> roles,
                                final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final String TAG = "setMovementTypes";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    HttpPost httpPost = new HttpPost(new StringBuilder().append(SERVER_URL)
                            .append("/api/v1/users/movement_types/").toString());

                    addAuthHeader(context, httpPost);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    JSONArray arr = new JSONArray();
                    for (Role role : roles)
                        if (role.checked)
                            arr.put(role.id);

                    JSONObject json = new JSONObject();
                    json.put("movement_type_ids", arr);

                    StringEntity se = new StringEntity(json.toString());
                    httpPost.setEntity(se);

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        NetworkManager.LOGS.error("Can't execute post request", e);
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        Event event = JsonHelper.jsonToEvent(data);
                        data.clear();

                        postRunnable.setResult(true);
                    } else {
                        postRunnable.setResult(false);
                    }

                    executeRunnable(context, postRunnable);
                } catch (Exception e) {
                    NetworkManager.LOGS.error("Can't create roles", e);
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setResult(false);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static void loginAtServer(final Context context, String login, String password,
                                     final DeliverResultRunnable<Boolean> postRunnable) {
        Map credentials = new HashMap();
        credentials.put("username", login);
        credentials.put("password", password);
        credentials.put("provider", SITE);

        loginAtServer(context, credentials, postRunnable);
    }

    public static void loginAtServer(final Context context, String token, int provider,
                                     final DeliverResultRunnable<Boolean> postRunnable) {
        LOGS.info("NetworkManager. loginAtServer() with Social Network access token");
        Map credentials = new HashMap();
        credentials.put("access_token", token);
        credentials.put("provider", provider);

        loginAtServer(context, credentials, postRunnable);
    }

    public static void loginAtServer(final Context context, final Map credentials,
                                final DeliverResultRunnable<Boolean> postRunnable) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final int CODE_SUCCESS = 200;
                final String TAG = "loginAtServer";
                try {
                    if (!Utils.isNetworkConnected(context, LOGS)) {
                        LOGS.info("NetworkManager. loginAtServer. No Network");
                        errorDialog(context, Utils.DIALOG_NO_CONNECTION);
                        throw new Exception();
                    }

                    StringBuilder url = new StringBuilder(SERVER_URL)
                            .append("/api/v1/auth/login/");

                    JSONObject json = new JSONObject();
                    int provider = (Integer) credentials.get("provider");
                    switch (provider) {
                        case SITE:
                            LOGS.info("NetworkManager. loginAtServer. Using login + password)");
                            url = url.append("site/");
                            json.put("username", credentials.get("username"));
                            json.put("password", credentials.get("password"));
                            break;
                        case FACEBOOK:
                            LOGS.info("NetworkManager. loginAtServer. Using FB access token");
                            url = url.append("facebook/");
                            json.put("access_token", credentials.get("access_token"));
                            break;
                    }
                    StringEntity se = new StringEntity(json.toString());

                    HttpPost httpPost = new HttpPost(url.toString());
                    httpPost.setEntity(se);
                    addUserAgentHeader(context, httpPost);
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");

                    Log.i(TAG, "request: " + httpPost.getRequestLine().toString());
                    Log.i(TAG, "request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    LOGS.info("EventManager. loginAtServer. Request: " + httpPost.getRequestLine().toString());
                    LOGS.info("EventManager. loginAtServer. Request entity: " + EntityUtils.toString(httpPost.getEntity()));

                    HttpResponse response = null;
                    try {
                        LOGS.info("EventManager. loginAtServer. Executing request");
                        response = httpClient.execute(httpPost);
                    } catch (Exception e) {
                        errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                        throw new Exception();
                    }

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responseContent = EntityUtils.toString(response.getEntity());
                    Log.i(TAG, "responseCode: " + responseCode);
                    Log.i(TAG, "responseContent: " + responseContent);

                    LOGS.info("EventManager. loginAtServer. ResponseCode: " + responseCode);
                    LOGS.info("EventManager. loginAtServer. ResponseContent: " + responseContent);

                    if (responseCode == CODE_SUCCESS) {
                        LOGS.info("EventManager. loginAtServer. Success");
                        Map<String, Object> data = mapper.readValue(responseContent, Map.class);
                        String api_username = String.valueOf(data.get("username"));
                        String api_key = String.valueOf(data.get("key"));

                        LOGS.info("EventManager. loginAtServer. got username: " + api_username +
                            "  got api_key: " + api_key + "  Saving it");

                        saveAuthData(context, api_username, api_key);

                        postRunnable.setResult(true);
                    } else {
                        LOGS.info("EventManager. loginAtServer. Failure");
                        postRunnable.setUnsuccessful(responseCode);
                    }
                    postRunnable.run();
                } catch (Exception e) {
                    LOGS.info("EventManager. loginAtServer. Can't login to server");
                    errorDialog(context, Utils.DIALOG_NETWORK_ERROR);
                    postRunnable.setUnsuccessful(0);
                    executeRunnable(context, postRunnable);
                }
            }
        });
    }

    public static class DeliverResultRunnable<Result> implements Runnable {

        private Result result;
        private boolean success = true;
        private int errorCode = -1;

        public void setResult(Result result) {
            this.result = result;
        }

        @Override
        public final void run() {
            if (success) {
                deliver(result);
            } else {
                onError(errorCode);
            }
        }

        public void setUnsuccessful(int errorCode) {
            this.success = false;
            this.errorCode = errorCode;
        }

        public void deliver(Result result) {}

        public void onError(int errorCode) {}
    }

    private static void executeRunnable(Context context, Runnable runnable) {
        if (runnable == null)
            return;
        else if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.runOnUiThread(runnable);
        } else
            runnable.run();
    }

    private static AbstractHttpMessage addAuthHeader(Context context, AbstractHttpMessage request) {
        request.addHeader(new BasicHeader("Authorization", new StringBuilder().append("ApiKey ")
                .append(Prefs.getApiUsername(context)).append(":")
                .append(Prefs.getApiKey(context)).toString()));
        Log.i("addAuthHeader", Prefs.getApiUsername(context) + ":" + Prefs.getApiKey(context));
        return request;
    }

    private static void saveAuthData(Context context, String api_username, String api_key) {
        Prefs.putApiUsername(context, api_username);
        Prefs.putApiKey(context, api_key);
    }

    public static void addUserAgentHeader(Context context, AbstractHttpMessage request) {
        String systemUserAgent = System.getProperty("http.agent");
        String customUserAgent = "";
        try {
            customUserAgent = new StringBuilder().append("p2psafety/")
                    // add app version
                    .append(context.getPackageManager()
                            .getPackageInfo(context.getPackageName(), 0).versionName).append(" ")
                            // split davlik machine version
                    .append(systemUserAgent.substring(systemUserAgent.indexOf('(', 0),
                            systemUserAgent.length())).toString();
            request.addHeader(new BasicHeader("User-Agent", customUserAgent));
            Log.i("getUserAgent", customUserAgent);
        } catch (PackageManager.NameNotFoundException e) {
            request.addHeader(new BasicHeader("User-Agent", systemUserAgent));
        }
    }
}
