package ua.p2psafety.fragments.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import ua.p2psafety.R;
import ua.p2psafety.SosActivity;
import ua.p2psafety.adapters.EmailsAdapter;
import ua.p2psafety.data.Prefs;
import ua.p2psafety.util.GmailOAuth2Sender;
import ua.p2psafety.util.Utils;

public class SetEmailsFragment extends Fragment {
    public static final String TAG = "SetEmailsFragment";
    private View vParent;
    private EmailsAdapter mAdapter;

    private View.OnClickListener lsnr = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (Utils.getEmail(getActivity()) == null) {
                Utils.showNoAccountDialog(getActivity());
                return;
            }

            if (Utils.isNetworkConnected(getActivity(), SosActivity.mLogs) && Prefs.getGmailToken(getActivity()) == null)
            {
                GmailOAuth2Sender sender = new GmailOAuth2Sender(getActivity());
                sender.initToken();
            }

            switch (v.getId()) {
                case R.id.ibtn_addemail:
                    if (mAdapter != null) {
                        EditText edt_addemail = (EditText) vParent.findViewById(R.id.edt_addemail);
                        String email = edt_addemail.getText().toString();
                        if (Utils.isEmailAddress(email)) {
                            if (email.length() > 0)
                                mAdapter.addEmail(email);
                            edt_addemail.setText("");
                        } else {
                            AlertDialog.Builder noEmailDialog = new AlertDialog.Builder(getActivity());
                            noEmailDialog.setMessage(getResources().getString(R.string.it_is_not_email))
                                    .setNeutralButton(android.R.string.ok, null)
                                    .show();
                            return;
                        }
                    }
                    break;
                case R.id.ibtn_addcontact:
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    // BoD con't: CONTENT_TYPE instead of CONTENT_ITEM_TYPE
                    intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
                    startActivityForResult(intent, 1001);

                    break;
            }
        }
    };

    public SetEmailsFragment() {
        super();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                Cursor c = null;
                try {
                    c = getActivity().getContentResolver().query(uri, new String[]{

                            ContactsContract.CommonDataKinds.Email.ADDRESS,
                            ContactsContract.CommonDataKinds.Phone.TYPE},
                            null, null, null);

                    if (c != null && c.moveToFirst()) {
                        String email = c.getString(0);
                        EditText edt_addemail = (EditText) vParent.findViewById(R.id.edt_addemail);
                        edt_addemail.setText(email);
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.setup_emails, container, false);
        ((SosActivity) getActivity()).getSupportActionBar().setHomeButtonEnabled(true);
        ((SosActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final View frame_indent = rootView.findViewById(R.id.frame_indent);
        frame_indent.setVisibility(View.VISIBLE);
        Typeface font = Typeface.createFromAsset(getActivity().getAssets(), "fonts/RobotoCondensed-Bold.ttf");

        vParent = rootView;
        mAdapter = new EmailsAdapter(getActivity());
        View ibtnAddPhone = vParent.findViewById(R.id.ibtn_addemail);
        ibtnAddPhone.setOnClickListener(lsnr);

        //check whether email contact picker is available
        final PackageManager packageManager = getActivity().getPackageManager();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // BoD con't: CONTENT_TYPE instead of CONTENT_ITEM_TYPE
        intent.setType(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.GET_ACTIVITIES);

        View ibtnAddContact = vParent.findViewById(R.id.ibtn_addcontact);
        if (list.size()>0)
        {
            ibtnAddContact.setOnClickListener(lsnr);
        }
        else
        {
            ibtnAddContact.setVisibility(View.INVISIBLE);
        }

        ListView lsv_phones = (ListView) vParent.findViewById(R.id.lsv_numbers);
        lsv_phones.setAdapter(mAdapter);

        ((TextView) rootView.findViewById(R.id.textView)).setTypeface(font);
        ((EditText) rootView.findViewById(R.id.edt_addemail)).setTypeface(Typeface.createFromAsset(getActivity().getAssets(),
                "fonts/RobotoCondensed-Light.ttf"));
        return rootView;
    }


}



