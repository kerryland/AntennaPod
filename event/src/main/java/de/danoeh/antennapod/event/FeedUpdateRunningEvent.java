package de.danoeh.antennapod.event;

public class FeedUpdateRunningEvent {
    public enum State {
        RUNNING, CANCELLED, FINISHED
    }

    public final State state;

    public FeedUpdateRunningEvent(State state) {
        this.state = state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }
}
