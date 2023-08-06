"""crowd_computing URL Configuration

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/3.2/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.urls import include, path
from crowd_computing.app import views

urlpatterns = [
        path('', views.Home.as_view(), name='home'),
        path('shows/<host_name>/<show_name>/', views.ShowView.as_view(), name='show'),
        path('shows/<host_name>/<show_name>/create_mc', views.CreateMC.as_view(), name='create_mcshow'),
        path('shows/<host_name>/<show_name>/manage/', views.ManageShowView.as_view(), name='manage_show'),
        path('shows/<host_name>/<show_name>/minimaps/metadata', views.MinimapMetadataView.as_view(), name='minimap_metadata'),
        path('shows/<host_name>/<show_name>/minimaps/<x>/<y>/<z>', views.MinimapView.as_view(), name='show_minimap'),
        path('shows/<host_name>/<show_name>/fetch_regions', views.FetchRegionsView.as_view(), name='fetch_regions'),
        path('minecraft/<method>', views.MinecraftView.as_view(), name='minecraft'),
        path('create_show', views.CreateShow.as_view(), name='create_show'),
        path('login', views.login, name='login'),
        path('oauth_return', views.oauth_return, name='oauth_return'),
        path('delete', views.delete, name='delete'),
]
