# Generated by Django 4.2.3 on 2023-08-04 14:04

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('app', '0010_crowdsource_range_show_last_update'),
    ]

    operations = [
        migrations.AddField(
            model_name='region',
            name='tags',
            field=models.CharField(blank=True, max_length=512),
        ),
    ]
