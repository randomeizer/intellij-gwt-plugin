package client;

record Schedule(java.time.LocalDate start, java.time.LocalDate end) {}

public class Java17Syntax {
  private static final String MESSAGE = """
      A GWT client text block
      """;

  public Object evaluate(Object candidate, int selection) {
    if (candidate instanceof java.time.LocalDate date) {
      return switch (selection) {
        case 0 -> date;
        default -> java.time.LocalDate.now();
      };
    }
    return MESSAGE.length();
  }
}
