from django.conf import settings
from django.forms import ModelForm, CharField
from django.views.generic import View, TemplateView, DetailView, ListView
from django.views.decorators.csrf import csrf_exempt
from django.http import HttpResponse, JsonResponse, Http404
from django.shortcuts import redirect, get_object_or_404
from django.middleware.csrf import get_token
from django.utils.crypto import get_random_string
from django.core.paginator import Paginator, EmptyPage
from authlib.integrations.django_client import OAuth

from crowd_computing.app.models import *

TWITCH_CLIENT_ID = "8lx8sze77v5scem3fxodn9g4rm1ngq"
if settings.DEBUG:
    TWITCH_CLIENT_SECRET = "FAKE"
else:
    with open('/etc/django/twitch_client_secret') as f:
        TWITCH_CLIENT_SECRET = f.read().strip()

TWITCH_CONF_URL="https://id.twitch.tv/oauth2/.well-known/openid-configuration"
oauth = OAuth()
oauth.register(
    name='twitch',
    server_metadata_url=TWITCH_CONF_URL,
    client_id=TWITCH_CLIENT_ID,
    client_secret=TWITCH_CLIENT_SECRET,
    client_kwargs={
        'scope': 'openid',
    }
)

def generate_auth_secret():
    chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    return get_random_string(50, chars)

class Redirect(Exception):
    def __init__(self, target):
        self.target = target

def with_oauth_signin(func):
    def wrapper(self, request, *args, **kwargs):
        context = self.get_context_data(**kwargs)
        if "viewer_id" not in request.session:
            context["signin_message"] = '<a href="#" onClick=\'login_window=window.open("/login","login_window","width=500,height=800"); login_window.opener=window; return false;\'>Click here to sign in!</a>'
        else:
            try:
                viewer = Viewer.objects.get(pk=request.session["viewer_id"])
                func(self, request, context, viewer, *args, **kwargs)
                context["signin_message"] = "Welcome, %s!" % viewer.twitch_username
            except Viewer.DoesNotExist:
                context["signin_message"] = "Your session seems to have gotten corrupted, please click here to sign in again."
            except Redirect as r:
                return redirect(r.target)
        return self.render_to_response(context)
    return wrapper

class ShowForm(ModelForm):
    display_name = CharField(required=False, max_length=100, help_text="A prettier version of the Show name, for display purposes.")
    class Meta:
        model = Show
        fields = [ "name", "display_name" ]
        help_texts = {
            "name": "The URL-safe name of the Show.  Spaces and underescores are interchangeable.",
        }

class Home(TemplateView):
    template_name = "home.html"

    @with_oauth_signin
    def get(self, request, context, viewer):
        context["me"] = viewer
        context["my_shows"] = Show.objects.filter(host=viewer)
        context["known_shows"] = {mc.show for mc in MiniConfig.objects.filter(viewer=viewer)}
        context["show_form"] = ShowForm()

class CreateShow(TemplateView):
    @with_oauth_signin
    def post(self, request, context, viewer):
        show = Show(host=viewer, minecraft_auth_secret=generate_auth_secret())
        form = ShowForm(request.POST, instance=show)
        if form.is_valid():
            form.save()
            raise Redirect("/shows/%s/%s/manage" % (viewer.twitch_username, show.name))

class MCForm(ModelForm):
    class Meta:
        model = MiniConfig
        fields = ["name", "source", "target", "limit"]
        help_texts = {
            "name": "The name of the Mini Config.  Spaces and underescores are interchangeable.",
            "source": "Which block region to grab items from.",
            "target": "Which block region to put items into.",
            "limit": "If positive, the maximum number of this kind of item in the target inventory.",
        }

class ShowView(TemplateView):
    template_name = "show.html"

    @with_oauth_signin
    def get(self, request, context, viewer, host, show):
        context["host"] = get_object_or_404(Viewer, twitch_username=host)
        context["show"] = get_object_or_404(Show, host=context["host"], name=show)
        context["me"] = viewer
        context["miniconfigs"] = MiniConfig.objects.filter(show=context["show"], viewer=context["me"])
        context["mc_form"] = MCForm()

class CreateMC(TemplateView):
    @with_oauth_signin
    def post(self, request, context, viewer, host, show):
        context["host"] = get_object_or_404(Viewer, twitch_username=host)
        context["show"] = get_object_or_404(Show, host=context["host"], name=show)
        mc = MiniConfig(show=context["show"], viewer=viewer)
        form = MCForm(request.POST, instance=mc)
        if form.is_valid():
            form.save()
            raise Redirect(".")

class ManageShowView(TemplateView):
    template_name = "manage_show.html"

    @with_oauth_signin
    def get(self, request, context, viewer, host, show):
        context["host"] = get_object_or_404(Viewer, twitch_username=host)
        context["show"] = get_object_or_404(Show, host=context["host"], name=show)
        context["me"] = viewer
        if context["me"] != context["host"]:
            raise Redirect("../")

class MinecraftView(View):
    @classmethod
    def as_view(cls, *args, **kwargs):
        return csrf_exempt(super().as_view(*args, **kwargs))

    def get(self, request, method):
        return HttpResponse("Secret Minecraf testing UI: <form method='post'><input type='text' name='auth_secret'><input type='submit' value='Send it!'></form>")

    def post(self, request, method):
        if "auth_secret" not in request.POST:
            return HttpResponse("auth_secret not provided: ", status=401)
        auth_secret = request.POST["auth_secret"]
        show = get_object_or_404(Show, minecraft_auth_secret=auth_secret)

        if method == "verify":
            return JsonResponse({
                "name": show.name,
                "display_name": show.display_name,
                "host_username": show.host.twitch_username,
            })
        if method == "get_all":
            p = Paginator(MiniConfig.objects.filter(show=show).order_by("pk"), 100)
            page = int(request.POST.get("page", 1))
            try:
                return JsonResponse({
                    "page": page,
                    "page_count": p.count,
                    "mini_configs": [mc.to_minecraft() for mc in p.page(page)],
                })
            except EmptyPage:
                return JsonResponse({
                    "page": 1,
                    "page_count": p.count,
                    "mini_configs": {},
                })
        if method == "get_specific":
            configs = []
            for full_name in request.POST["names"].split(","):
                viewer_username, name = full_name.split(",")
                try:
                    configs.append(MiniConfig.objects.get(show=show, name=name, viewer__twitch_username=viewer_username))
                except MiniConfig.DoesNotExist:
                    pass
            return JsonResponse({"mini_configs": [mc.to_minecraft() for mc in configs]})
        if method == "add_region":
            region = Region(
                show=show,
                name=request.POST["name"],
                start_x=request.POST["start_x"],
                start_y=request.POST["start_y"],
                start_z=request.POST["start_z"],
                end_x=request.POST["end_x"],
                end_y=request.POST["end_y"],
                end_z=request.POST["end_z"],
            )
            region.save()
            return JsonResponse({})
        raise Http404()

def login(request):
    redirect_uri = "https://crowd-computing.funnyman3595.com/oauth_return"
    return oauth.twitch.authorize_redirect(request, redirect_uri)

def oauth_return(request):
    token = oauth.twitch.authorize_access_token(request, client_id=TWITCH_CLIENT_ID, client_secret=TWITCH_CLIENT_SECRET)
    user = token["userinfo"]
    try:
        viewer = Viewer.objects.get(twitch_id=user["sub"])
        request.session["viewer_id"] = viewer.pk
        if viewer.twitch_username != user["preferred_username"]:
            viewer.twitch_username = user["preferred_username"]
            viewer.save()
    except Viewer.DoesNotExist:
        viewer = Viewer(
            twitch_id=user["sub"],
            twitch_username=user["preferred_username"],
        )
        viewer.save()
        request.session["viewer_id"] = viewer.pk
    return HttpResponse("<body onload='window.opener.location.reload(false); window.close()'></body>")

def delete(request):
    if request.method != "POST":
        return HttpResponse("Are you sure? <form method=post><input type='submit' value='Delete it all!'><input type='hidden' name='csrfmiddlewaretoken' value='" + get_token(request) + "'></form>")
    else:
        if "viewer_id" not in request.session:
            request.session.flush()
            return HttpResponse("You weren't signed in, so only your session has been deleted.  Goodbye!")
        try:
            viewer = Viewer.objects.get(pk=request.session["viewer_id"])
            viewer.delete()
            request.session.flush()
            return HttpResponse("Everything deleted.  Goodbye!")
        except Viewer.DoesNotExist:
            request.session.flush()
            return HttpResponse("You didn't have a valid signin, so only your session has been deleted.  Goodbye!")