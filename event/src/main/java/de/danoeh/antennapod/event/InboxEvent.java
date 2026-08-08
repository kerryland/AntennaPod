package de.danoeh.antennapod.event;

public class InboxEvent {
    public InboxEvent(Action action) {
        this.action = action;
    }

    public enum Action {
        REMOVED // item(s) were removed from inbox
    }

    public final Action action;
}
