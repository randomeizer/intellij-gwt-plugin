package org.randombits.intellij.gwt.run.remoteUi.responses;

public abstract class RemoteUiResponse {
  private final int myMessageId;

  public RemoteUiResponse(int messageId) {
    myMessageId = messageId;
  }

  public int getMessageId() {
    return myMessageId;
  }
}
