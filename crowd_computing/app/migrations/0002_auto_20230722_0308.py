# Generated by Django 3.2.19 on 2023-07-22 03:08

from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('app', '0001_initial'),
    ]

    operations = [
        migrations.RemoveField(
            model_name='viewer',
            name='twitch_oauth_id_token',
        ),
        migrations.RemoveField(
            model_name='viewer',
            name='twitch_oauth_refresh_token',
        ),
    ]