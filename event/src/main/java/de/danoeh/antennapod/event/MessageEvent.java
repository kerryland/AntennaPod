package de.danoeh.antennapod.event;

import android.content.Context;
import androidx.annotation.Nullable;

import androidx.core.util.Consumer;

public class MessageEvent {

    public final String message;

    @Nullable
    public final Consumer<Context> action;

    @Nullable
    public final String actionText;

    @Nullable
    public final String dismissText;

    /** Whether the message should stay on screen until the user dismisses it. */
    public final boolean indefinite;

    public MessageEvent(String message) {
        this(message, null, null, null, true);
    }

    public MessageEvent(String message, Consumer<Context> action, String actionText) {
        this(message, action, actionText, null, false);
    }

    public MessageEvent(String message, @Nullable Consumer<Context> action, @Nullable String actionText,
                        boolean indefinite) {
        this(message, action, actionText, null, indefinite);
    }

    public MessageEvent(String message, @Nullable Consumer<Context> action, @Nullable String actionText,
                        @Nullable String dismissText, boolean indefinite) {
        this.message = message;
        this.action = action;
        this.actionText = actionText;
        this.dismissText = dismissText;
        this.indefinite = indefinite;
    }
}
