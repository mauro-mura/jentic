package dev.jentic.examples.support.model;

/**
 * Supported intents for the support chatbot.
 */
public enum SupportIntent {
    FAQ("faq", "General questions and help articles"),
    ACCOUNT("account", "Account management, profile, settings"),
    TRANSACTION("transaction", "Transaction history, payments, transfers"),
    SECURITY("security", "Password, 2FA, device management"),
    BUDGET("budget", "Budget creation, tracking, alerts"),
    ESCALATE("escalate", "Transfer to human agent"),
    UNKNOWN("unknown", "Unrecognized intent");
    
    private final String code;
    private final String description;
    
    SupportIntent(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String code() { return code; }
    public String description() { return description; }
}
