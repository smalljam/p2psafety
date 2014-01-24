from django.conf import settings
from django.shortcuts import render

from annoying.decorators import render_to


@render_to('events/map.html')
def map(request):
    return {
        'GOOGLE_API_KEY': settings.GOOGLE_API_KEY
    }