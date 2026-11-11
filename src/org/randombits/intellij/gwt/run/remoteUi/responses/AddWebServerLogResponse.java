package org.randombits.intellij.gwt.run.remoteUi.responses;

import org.randombits.intellij.gwt.run.remoteUi.DevModeLogDescriptor;

public class AddWebServerLogResponse extends AddLogResponseBase {
  public AddWebServerLogResponse(int messageId) {
    super(messageId);
  }

  @Override
  public DevModeLogDescriptor createLogDescriptor() {
    return DevModeLogDescriptor.SERVER_LOG_DESCRIPTOR;
  }
}
