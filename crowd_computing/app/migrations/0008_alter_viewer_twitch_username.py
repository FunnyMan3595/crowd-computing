# Generated by Django 3.2.19 on 2023-07-24 00:56

import crowd_computing.app.models
from django.db import migrations


class Migration(migrations.Migration):

    dependencies = [
        ('app', '0007_auto_20230723_0227'),
    ]

    operations = [
        migrations.AlterField(
            model_name='viewer',
            name='twitch_username',
            field=crowd_computing.app.models.UrlSafeCharField(max_length=30, unique=True),
        ),
    ]
