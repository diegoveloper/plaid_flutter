package com.github.jorgefspereira.plaid_flutter;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.common.BinaryMessenger;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

import com.plaid.link.BuildConfig;
import com.plaid.link.Plaid;
import com.plaid.linkbase.models.LinkCancellation;
import com.plaid.linkbase.models.LinkConfiguration;
import com.plaid.linkbase.models.LinkConnection;
import com.plaid.linkbase.models.LinkConnectionMetadata;
import com.plaid.linkbase.models.LinkEvent;
import com.plaid.linkbase.models.LinkEventListener;
import com.plaid.linkbase.models.LinkEventMetadata;
import com.plaid.linkbase.models.LinkExitMetadata;
import com.plaid.linkbase.models.PlaidApiError;
import com.plaid.linkbase.models.PlaidEnvironment;
import com.plaid.linkbase.models.PlaidLinkActivityResultHandler;
import com.plaid.linkbase.models.PlaidOptions;
import com.plaid.linkbase.models.PlaidProduct;
import com.plaid.linkbase.models.LinkAccount;
import com.plaid.plog.LogLevel;


/** PlaidFlutterPlugin */
public class PlaidFlutterPlugin implements MethodCallHandler, PluginRegistry.ActivityResultListener {

  private Activity activity;
  private MethodChannel channel;

  private static final String CHANNEL_NAME = "plugins.flutter.io/plaid_flutter";
  private static final int LINK_REQUEST_CODE = 1;

  private PlaidLinkActivityResultHandler plaidLinkActivityResultHandler = new PlaidLinkActivityResultHandler(
      LINK_REQUEST_CODE,
      new Function1<LinkConnection, Unit>() {
        @Override
        public Unit invoke(LinkConnection e) {
          Map<String, Object> data = new HashMap<>();

          data.put("publicToken", e.getPublicToken());
          data.put("metadata", createMapFromConnectionMetadata(e.getLinkConnectionMetadata()));

          channel.invokeMethod("onAccountLinked", data);
          return Unit.INSTANCE;
        }
      },
      new Function1<LinkCancellation, Unit>() {
        @Override
        public Unit invoke(LinkCancellation e) {
          Map<String, Object> data = new HashMap<>();
          Map<String, String> metadata = new HashMap<>();

          metadata.put("institution_name", e.getInstitutionName());
          metadata.put("exit_status", e.getExitStatus());
          metadata.put("link_session_id", e.getLinkSessionId());
          metadata.put("institution_id", e.getInstitutionId());
          metadata.put("status", e.getStatus());

          data.put("metadata", metadata);

          channel.invokeMethod("onExit", data);
          return Unit.INSTANCE;
        }
      },
      new Function1<PlaidApiError, Unit>() {
        @Override
        public Unit invoke(PlaidApiError e) {
          Map<String, Object> data = new HashMap<>();

          data.put("error", e.getErrorMessage());
          data.put("metadata", createMapFromExitMetadata(e.getLinkExitMetadata()));

          channel.invokeMethod("onAccountLinkError", data);
          return Unit.INSTANCE;
        }
      },
      new Function1<Throwable, Unit>() {
        @Override
        public Unit invoke(Throwable e) {
          return Unit.INSTANCE;
        }
      }
  );

  public static void registerWith(Registrar registrar) {
    if (registrar.activity() == null) {
      return;
    }

    final PlaidFlutterPlugin plugin = new PlaidFlutterPlugin();
    plugin.initializePlugin(registrar.activity(), registrar.messenger());
    registrar.addActivityResultListener(plugin);
  }

  private void initializePlugin(Activity activity, BinaryMessenger messenger) {
    this.activity = activity;

    this.channel = new MethodChannel(messenger, CHANNEL_NAME);
    channel.setMethodCallHandler(this);

    PlaidOptions plaidOptions = new PlaidOptions.Builder()
            .logLevel(BuildConfig.DEBUG ? LogLevel.DEBUG : LogLevel.ASSERT)
            .build();

    Plaid.create(activity.getApplication(), plaidOptions);
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("open")) {

      Map<String, Object> arguments = call.arguments();

      String clientName = (String) arguments.get("clientName");

      String envString = (String)arguments.get("env");
      PlaidEnvironment env = PlaidEnvironment.valueOf(envString.toUpperCase());

      String webhook = (String) arguments.get("webhook");
      String webviewRedirectUri = (String) arguments.get("oauthRedirectUri");
      Log.i("TESTE", webviewRedirectUri);

      ArrayList<PlaidProduct> products = new ArrayList<>();
      ArrayList<?> productsObjects = (ArrayList<?>)arguments.get("products");

      for (Object po : productsObjects) {
        String ps = (String)po;
        PlaidProduct p = PlaidProduct.valueOf(ps.toUpperCase());
        products.add(p);
      }

      final LinkConfiguration configuration = new LinkConfiguration.Builder(clientName, products, webviewRedirectUri)
              .environment(env)
              .webhook(webhook)
              .build();

      Plaid.setLinkEventListener(new LinkEventListener(new Function1<LinkEvent, Unit>() {
          @Override
          public Unit invoke(LinkEvent e) {
            Map<String, Object> data = new HashMap<>();
            data.put("event", e.getEventName());
            data.put("metadata", createMapFromEventMetadata(e.getMetadata()));

            channel.invokeMethod("onEvent", data);
            return Unit.INSTANCE;
          }
        }
      ));

      Plaid.openLink(activity, configuration, LINK_REQUEST_CODE);

    } else {
      result.notImplemented();
    }
  }

  @Override
  public boolean onActivityResult(int requestCode, int resultCode, Intent intent) {
    return plaidLinkActivityResultHandler.onActivityResult(requestCode, resultCode, intent);
  }

  private Map<String, String> createMapFromEventMetadata(LinkEventMetadata data) {
    Map<String, String> result = new HashMap<>();

    result.put("institution_name", data.getInstitutionName());
    result.put("mfa_type", data.getMfaType());
    result.put("request_id", data.getRequestId());
    result.put("error_message", data.getErrorMessage());
    result.put("timestamp", data.getTimestamp());
    result.put("link_session_id", data.getLinkSessionId());
    result.put("error_code", data.getErrorCode());
    result.put("exit_status", data.getExitStatus());
    result.put("institution_id", data.getInstitutionId());
    result.put("institution_search_query", data.getInstitutionSearchQuery());
    result.put("view_name", data.getViewName().name());
    result.put("error_type", data.getErrorType());

    return result;
  }

  private Map<String, Object> createMapFromConnectionMetadata(LinkConnectionMetadata data) {
    Map<String, Object> result = new HashMap<>();

    result.put("institution_name", data.getInstitutionName());
    result.put("link_session_id", data.getLinkSessionId());
    result.put("institution_id", data.getInstitutionId());

    ArrayList<Object> accounts = new ArrayList<>();

    for (LinkAccount a: data.getAccounts()) {
      Map<String, String> aux = new HashMap<>();
      aux.put("id", a.getAccountId());
      aux.put("mask", a.getAccountNumber());
      aux.put("name", a.getAccountName());
      aux.put("type", a.getAccountType());
      aux.put("subtype", a.getAccountSubType());
      accounts.add(aux);
    }

    result.put("accounts", accounts);
    result.put("account", accounts.get(0));

    return result;
  }

  private Map<String, String> createMapFromExitMetadata(LinkExitMetadata data) {
    Map<String, String> result = new HashMap<>();

    result.put("institution_name", data.getInstitutionName());
    result.put("request_id", data.getRequestId());
    result.put("link_session_id", data.getLinkSessionId());
    result.put("institution_id", data.getInstitutionId());
    result.put("status", data.getStatus());

    return result;
  }
}

