package com.google.ase.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;

import com.google.ase.AseLog;
import com.google.ase.AsyncTaskListener;
import com.google.ase.InterpreterInstaller;
import com.google.ase.InterpreterUninstaller;
import com.google.ase.exception.AseException;
import com.google.ase.interpreter.InterpreterConstants;
import com.google.ase.interpreter.InterpreterDescriptor;

public abstract class Main extends Activity {
  protected final static float MARGIN_DIP = 3.0f;

  protected SharedPreferences mPreferences;
  protected InterpreterDescriptor mDescriptor;
  protected Button mButton;

  protected final String ID = getID();

  protected String getID() {
    return getClass().getPackage().getName();
  }

  protected abstract InterpreterDescriptor getDescriptor();

  protected abstract InterpreterInstaller getInterpreterInstaller(InterpreterDescriptor descriptor,
      Context context, AsyncTaskListener<Boolean> listener) throws AseException;

  protected abstract InterpreterUninstaller getInterpreterUninstaller(InterpreterDescriptor descriptor,
      Context context, AsyncTaskListener<Boolean> listener) throws AseException;

  protected enum RunningTask {
    INSTALL, UNINSTALL
  }

  protected volatile RunningTask CurrentTask = null;

  protected final AsyncTaskListener<Boolean> mTaskListener = new AsyncTaskListener<Boolean>() {
    @Override
    public void onTaskFinished(Boolean result, String message) {
      if (result) {
        switch (CurrentTask) {
          case INSTALL:
            updatePreferences(true);
            prepareUninstallButton();
            break;
          case UNINSTALL:
            updatePreferences(false);
            prepareInstallButton();
            break;
        }
      }
      AseLog.v(Main.this, message);
      CurrentTask = null;
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    mDescriptor = getDescriptor();
    setUI();
    if (checkInstalled()) {
      prepareUninstallButton();
    } else {
      prepareInstallButton();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  protected void setUI() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

    mButton = new Button(this);
    MarginLayoutParams marginParams =
        new MarginLayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
    final float scale = getResources().getDisplayMetrics().density;
    int marginPixels = (int) (MARGIN_DIP * scale + 0.5f);
    marginParams.setMargins(marginPixels, marginPixels, marginPixels, marginPixels);
    mButton.setLayoutParams(marginParams);
    layout.addView(mButton);

    setContentView(layout);
  }

  protected void prepareInstallButton() {
    mButton.setText("Install");
    mButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        install();
      }
    });
  }

  protected void prepareUninstallButton() {
    mButton.setText("Uninstall");
    mButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        uninstall();
      }
    });
  }

  protected void sendBroadcast(boolean isInterpreterInstalled) {
    Intent intent = new Intent();
    intent.setData(Uri.parse("package:" + ID));
    if (isInterpreterInstalled) {
      intent.setAction(InterpreterConstants.ACTION_INTERPRETER_ADDED);
    } else {
      intent.setAction(InterpreterConstants.ACTION_INTERPRETER_REMOVED);
    }
    this.sendBroadcast(intent);
  }

  protected synchronized void install() {
    if (CurrentTask != null) {
      return;
    }
    CurrentTask = RunningTask.INSTALL;
    InterpreterInstaller installTask;
    try {
      installTask = getInterpreterInstaller(mDescriptor, Main.this, mTaskListener);
    } catch (AseException e) {
      AseLog.e(this, e.getMessage(), e);
      return;
    }
    installTask.execute();
  }

  protected synchronized void uninstall() {
    if (CurrentTask != null) {
      return;
    }
    CurrentTask = RunningTask.UNINSTALL;
    InterpreterUninstaller uninstallTask;
    try {
      uninstallTask = getInterpreterUninstaller(mDescriptor, Main.this, mTaskListener);
    } catch (AseException e) {
      AseLog.e(this, e.getMessage(), e);
      return;
    }
    uninstallTask.execute();
  }

  protected void updatePreferences(boolean isInstalled) {
    SharedPreferences.Editor editor = mPreferences.edit();
    editor.putBoolean(InterpreterConstants.INSTALL_PREF, isInstalled);
    editor.commit();
    sendBroadcast(isInstalled);
  }

  protected boolean checkInstalled() {
    boolean isInstalled = mPreferences.getBoolean(InterpreterConstants.INSTALL_PREF, false);
    sendBroadcast(isInstalled);
    return isInstalled;
  }
}