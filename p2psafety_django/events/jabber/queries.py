import logging

from django.conf import settings
from django.contrib.auth.models import User

from . import logger
from .clients import get_client


__all__ = ['on_user_created', 'notify_supporters', 'notify_supporter']


def on_user_created(new_user):
    """
    This function should be called for newly registered user.

    :type new_user: `django.contrib.auth.models.User`
    """
    with get_client('UsersClient') as client:
        client.create_user(new_user)


def notify_supporters(event):
    """
    Sends notifications to event's supporters via jabber node.

    :type event: :class:`events.models.Event`
    """
    from events.api.resources import EventResource

    # Constructing payload
    resource = EventResource()
    event_dict = resource.full_dehydrate(resource.build_bundle(obj=event))
    payload = resource.serialize(None, event_dict, 'application/xml')

    with get_client('PubsubClient') as client:
        client.publish(payload)


def notify_supporter(event, supporter):
    raise NotImplementedError
