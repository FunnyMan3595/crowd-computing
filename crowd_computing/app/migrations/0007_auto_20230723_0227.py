# Generated by Django 3.2.19 on 2023-07-23 02:27

from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ('app', '0006_show_minecraft_auth_secret'),
    ]

    operations = [
        migrations.AlterField(
            model_name='miniconfig',
            name='source',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='source', to='app.region'),
        ),
        migrations.AlterField(
            model_name='miniconfig',
            name='target',
            field=models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='target', to='app.region'),
        ),
        migrations.AlterField(
            model_name='show',
            name='display_name',
            field=models.CharField(blank=True, max_length=100, null=True),
        ),
    ]
