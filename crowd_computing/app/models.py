from django.db import models
from django.core.validators import RegexValidator

URLSAFE_PLUS_SPACE_VALIDATOR = RegexValidator("^[a-zA-Z0-9_ ]+$", message="Must contain only letters, numbers, underscores, and spaces.")

class UrlSafeCharField(models.CharField):
    def validate(self, value, model_instance):
        URLSAFE_PLUS_SPACE_VALIDATOR(value)

    def clean(self, value, model_instance):
        value = super().clean(value, model_instance)
        return value.replace(" ", "_")

class Viewer(models.Model):
    twitch_username = UrlSafeCharField(max_length=30, unique=True)
    twitch_id = models.IntegerField()

    def __str__(self):
        return self.twitch_username

class Show(models.Model):
    class Meta:
        constraints = [models.UniqueConstraint(fields=("host", "name"), name="unique_show_identifier")]

    host = models.ForeignKey(Viewer, on_delete=models.CASCADE)
    name = UrlSafeCharField(max_length=30)
    display_name = models.CharField(max_length=100, null=True, blank=True)
    minecraft_auth_secret = models.CharField(max_length=100)
    last_update = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.display_name

class CrowdSource(models.Model):
    class Meta:
        constraints = [models.UniqueConstraint(fields=("show", "x", "y", "z"), name="unique_crowd_source_identifier")]

    show = models.ForeignKey(Show, on_delete=models.CASCADE)
    x = models.IntegerField()
    y = models.IntegerField()
    z = models.IntegerField()
    range = models.IntegerField()
    minimap = models.BinaryField(max_length=100*1024)

    def __str__(self):
        return "(%d, %d, %d)" % (x, y, z)

class Region(models.Model):
    class Meta:
        constraints = [models.UniqueConstraint(fields=("show", "name"), name="unique_region_identifier")]

    show = models.ForeignKey(Show, on_delete=models.CASCADE)
    name = models.CharField(max_length=100)
    start_x = models.IntegerField()
    start_y = models.IntegerField()
    start_z = models.IntegerField()
    end_x = models.IntegerField()
    end_y = models.IntegerField()
    end_z = models.IntegerField()

    def __str__(self):
        return self.name

    def to_minecraft(self):
        return {
            "start_x": self.start_x,
            "start_y": self.start_y,
            "start_z": self.start_z,
            "end_x": self.end_x,
            "end_y": self.end_y,
            "end_z": self.end_z,
        }

class MiniConfig(models.Model):
    class Meta:
        constraints = [models.UniqueConstraint(fields=("show", "viewer", "name"), name="unique_miniconfig_identifier")]

    show = models.ForeignKey(Show, on_delete=models.CASCADE)
    viewer = models.ForeignKey(Viewer, on_delete=models.CASCADE)
    name = UrlSafeCharField(max_length=30)
    source = models.ForeignKey(Region, related_name="source", null=True, on_delete=models.SET_NULL, blank=True)
    target = models.ForeignKey(Region, related_name="target", null=True, on_delete=models.SET_NULL, blank=True)
    limit = models.IntegerField(default=-1)

    def __str__(self):
        return self.name

    def to_minecraft(self):
        minecraft = {
            "viewer": self.viewer.twitch_username,
            "name": self.name,
            "limit": self.limit,
        }
        if self.source:
            minecraft["source"] = self.source.to_minecraft()
        if self.target:
            minecraft["target"] = self.target.to_minecraft()
        return minecraft
