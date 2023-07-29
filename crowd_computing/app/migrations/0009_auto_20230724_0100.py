# Generated by Django 3.2.19 on 2023-07-24 01:00

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('app', '0008_alter_viewer_twitch_username'),
    ]

    operations = [
        migrations.AddConstraint(
            model_name='crowdsource',
            constraint=models.UniqueConstraint(fields=('show', 'x', 'y', 'z'), name='unique_crowd_source_identifier'),
        ),
        migrations.AddConstraint(
            model_name='miniconfig',
            constraint=models.UniqueConstraint(fields=('show', 'viewer', 'name'), name='unique_miniconfig_identifier'),
        ),
        migrations.AddConstraint(
            model_name='region',
            constraint=models.UniqueConstraint(fields=('show', 'name'), name='unique_region_identifier'),
        ),
        migrations.AddConstraint(
            model_name='show',
            constraint=models.UniqueConstraint(fields=('host', 'name'), name='unique_show_identifier'),
        ),
    ]
