from django.core.urlresolvers import reverse
from django.test import TestCase

from tastypie.test import ResourceTestCase

from .helpers.mixins import UsersMixin
from .helpers.factories import EventFactory
from users.tests.helpers import UserFactory


class ViewsTestCase(UsersMixin, TestCase):

    def test_events_map(self):
        url = reverse('events:map')
        self.assertEqual(self.client.get(url).status_code, 302)
        self.login_as(self.events_granted_user)
        self.assertEqual(self.client.get(url).status_code, 200)


class OperatorTestCase(UsersMixin, ResourceTestCase):

    def test_add_eventupdate_ok(self):
        user = UserFactory()
        event = EventFactory(user=user)
        url = reverse('events:operator_add_eventupdate')

        self.login_as_superuser()
        data = dict(event_id=event.id, text='test')
        resp = self.api_client.post(url, data=data)
        self.assertValidJSONResponse(resp)
        self.assertTrue(self.deserialize(resp)['success'])

    def test_add_eventupdate_errors(self):
        user, operator = UserFactory(), self.superuser
        event = EventFactory(user=user)
        url = reverse('events:operator_add_eventupdate')
        valid_data = dict(event_id=event.id, text='test')

        # No permissions
        self.login_as_user()
        self.assertHttpForbidden(self.api_client.post(url, data=valid_data))

        self.login_as_superuser()
        
        # No text
        data = dict(event_id=event.id)
        self.assertHttpBadRequest(self.api_client.post(url, data=data))

        # Invalid id
        data = dict(event_id='test')
        self.assertHttpBadRequest(self.api_client.post(url, data=data))

        # No such event
        data = dict(text='test', event_id=event.id+1)
        self.assertHttpNotFound(self.api_client.post(url, data=data))        
