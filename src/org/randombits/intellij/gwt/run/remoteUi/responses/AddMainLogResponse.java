package org.randombits.intellij.gwt.run.remoteUi.responses;

import org.randombits.intellij.gwt.run.remoteUi.DevModeLogDescriptor;

public class AddMainLogResponse extends AddLogResponseBase {
  public AddMainLogResponse(int messageId) {
    super(messageId);
  }

  @Override
  public DevModeLogDescriptor createLogDescriptor() {
    return DevModeLogDescriptor.MAIN_LOG_DESCRIPTOR;
  }
}
