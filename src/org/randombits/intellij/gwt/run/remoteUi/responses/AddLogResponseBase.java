package org.randombits.intellij.gwt.run.remoteUi.responses;

import org.randombits.intellij.gwt.run.remoteUi.DevModeLogDescriptor;

public abstract class AddLogResponseBase extends RemoteUiResponse {
  public AddLogResponseBase(int messageId) {
    super(messageId);
  }

  public abstract DevModeLogDescriptor createLogDescriptor();
}
