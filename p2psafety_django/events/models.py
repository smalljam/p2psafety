# -*- coding: utf-8 -*-
import uuid
import hmac
import datetime

from django.db import models
from django.dispatch import receiver
from django.conf import settings
from django.contrib.auth.models import User
from django.contrib.gis.db import models as geomodels
from django.utils import timezone

from livesettings import config_value


try:
    from hashlib import sha1
except ImportError:
    import sha
    sha1 = sha.sha


import jabber
from .managers import EventManager


class Event(models.Model):
    """
    Event class.

    Event that receives at least one :class:`EventUpdate` becomes active. User
    can has only one active event at the same time.

    As some events can "support" other events, there are ``supported`` and
    ``supporters`` fields.
    """
    STATUS_ACTIVE = 'A'
    STATUS_PASSIVE = 'P'
    STATUS_FINISHED = 'F'
    STATUS = (
        (STATUS_ACTIVE, 'Active'),
        (STATUS_PASSIVE, 'Passive'),
        (STATUS_FINISHED, 'Finished'),
    )
    TYPE_VICTIM = 0
    TYPE_SUPPORT = 1
    EVENT_TYPE = (
        (TYPE_VICTIM, 'victim'),
        (TYPE_SUPPORT, 'support'),
    )

    class Meta:
        permissions = (
            ("view_event", "Can view event"),
        )

    objects = EventManager()

    user = models.ForeignKey(User, related_name='events')

    PIN = models.IntegerField(default=0)
    key = models.CharField(max_length=128, blank=True, default='', db_index=True)
    status = models.CharField(max_length=1, choices=STATUS, default=STATUS_PASSIVE)
    type = models.IntegerField(choices=EVENT_TYPE, default=TYPE_VICTIM)
    supported = models.ManyToManyField('self', symmetrical=False,
        related_name='supporters', blank=True)
    watchdog_task_id = models.CharField(max_length=36, blank=True, null=True)

    def __unicode__(self):
        return u"{} event by {}".format(self.status, self.user)

    @property
    def latest_update(self):
        try:
            return self.updates.latest()
        except EventUpdate.DoesNotExist:
            return None

    @property
    def latest_location(self):
        try:
            updates = self.updates.filter(location__isnull=False)
            return updates.latest().location
        except EventUpdate.DoesNotExist:
            return None

    @property
    def latest_text(self):
        try:
            return self.updates.exclude(text='').latest().text
        except EventUpdate.DoesNotExist:
            return None

    @property
    def related_users(self):
        """
        Returns user ids of self and all related events.
        """
        sd = list(self.supported.all().values_list('user', flat=True))
        ss = list(self.supporters.all().values_list('user', flat=True))
        u = [self.user.id, ]
        return list(u + sd + ss)

    def save(self, *args, **kwargs):
        """
        Basic save + generator until PIN is unique.
        """
        if not self.key:
            bad_key = True
            while bad_key:
                self.PIN, self.key = self.generate_keys()
                bad_key = (Event.objects.filter(PIN=self.PIN)
                                        .exclude(status=self.STATUS_FINISHED)
                                        .exists())
        super(Event, self).save(*args, **kwargs)

    def support_by_user(self, user):
        """
        Binds provided user to this event as "supporter".

        Raises: ``DoesNotExist`` if user has no current event.
        """
        supports_event = Event.objects.get_current_of(user)
        if supports_event.type != self.TYPE_SUPPORT:
            supports_event.type = self.TYPE_SUPPORT
            supports_event.save()

        self.supporters.add(supports_event)

    def notify_supporters(self):
        jabber.notify_supporters(self)

    def generate_keys(self):
        """
        Generates uuid, and PIN.
        """
        new_uuid = uuid.uuid4()
        PIN = new_uuid.int % 1000000
        key = hmac.new(new_uuid.bytes, digestmod=sha1).hexdigest()
        return (PIN, key)


@receiver(models.signals.post_save, sender=Event)
def mark_old_events_as_finished(sender, **kwargs):
    """
    Every user has only one active or passive event. Design decision.
    """
    if kwargs.get('created'):
        event = kwargs['instance']
        (Event.objects.filter(user=event.user)
                      .exclude(status=Event.STATUS_FINISHED)
                      .exclude(id=event.id)
                      .update(status=Event.STATUS_FINISHED))


class EventUpdate(models.Model):
    """
    Event update. Stores any kind of additional information for event.
    """
    class Meta:
        permissions = (
            ("view_eventupdate", "Can view event update"),
        )
        ordering = ('-timestamp',)
        get_latest_by = 'timestamp'

    user = models.ForeignKey(User, related_name='event_owner', blank=True, null=True)
    event = models.ForeignKey(Event, related_name='updates')
    timestamp = models.DateTimeField(default=timezone.now)
    active = models.BooleanField(default=True)

    text = models.TextField(blank=True)
    location = geomodels.PointField(srid=settings.SRID['default'], blank=True, null=True)
    audio = models.FileField(upload_to='audio', blank=True, null=True)
    video = models.FileField(upload_to='video', blank=True, null=True)

    objects = geomodels.GeoManager()

    def activate_event(self):
        """Activate event if it was passive"""
        all_events_are_finished = not self.event.user.events.filter(
            status__in=[Event.STATUS_PASSIVE, Event.STATUS_ACTIVE]).exists()
        if self.event.status == Event.STATUS_PASSIVE or all_events_are_finished:
            self.event.status = Event.STATUS_ACTIVE
            self.event.save()
            if (self.event.type == Event.TYPE_VICTIM and
                config_value('Events', 'supporters-autonotify')):
                self.event.notify_supporters()

    def start_watchdog(self):
        """Start watchdog (passive safe mode)
        before calling you should check is there no other watchdog for this event
        if  not self.event.watchdog_task_id:
        """
        from .tasks import eventupdate_watchdog
        delay = self.delay if hasattr(self,'delay') else settings.WATCHDOG_DELAY
        delay = datetime.timedelta(seconds=delay)
        eta = datetime.datetime.now()+delay
        res = eventupdate_watchdog.apply_async((self.event.id, delay),eta=eta)
        self.event.watchdog_task_id=res.task_id
        self.event.save()

    def save(self, *args, **kwargs):
        created = self.pk is None
        super(EventUpdate, self).save(*args, **kwargs)

        if created:
            # Event that received an acitve update becomes active.
            if self.active:
                self.activate_event()
            #passive update
            elif not self.active and not self.event.watchdog_task_id:
                self.start_watchdog()
