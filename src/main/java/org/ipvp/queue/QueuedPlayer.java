package org.ipvp.queue;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class QueuedPlayer {

    private ProxiedPlayer handle;
    private int priority;
    private Queue queue;
    private LocalDateTime queueEnterTime;

    public QueuedPlayer(ProxiedPlayer handle, int priority) {
        Objects.requireNonNull(handle, "Player cannot be null");
        this.handle = handle;
        this.priority = priority;
    }

    /**
     * Returns the ProxiedPlayer represented by this instance
     *
     * @return ProxiedPlayer handle
     */
    public ProxiedPlayer getHandle() {
        return handle;
    }

    /**
     * Returns the priority rank information about this player
     *
     * @return Priority information
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Returns the current queue this player is in
     *
     * @return Queue entered, or null
     */
    public Queue getQueue() {
        return queue;
    }

    /**
     * Returns whether or not the player is in a queue
     *
     * @return True if the player is in a queue, false otherwise
     */
    public boolean isInQueue() {
        return queue != null;
    }

    /**
     * Returns the current position inside the queue the player is waiting for
     *
     * @return Queue position
     * @throws IllegalStateException When the player is not in a queue
     */
    public int getPosition() {
        if (!isInQueue()) {
            throw new IllegalStateException("Player is not queued");
        }
        for (int i = 0 ; i < queue.size() ; i++) {
            if (queue.get(i).equals(this)) {
                return i;
            }
        }
        return -1;
    }

    public int getSecondsInQueue() {
        LocalDateTime now = LocalDateTime.now();
        long time = Math.round(ChronoUnit.MILLIS.between(queueEnterTime, now) / 1000.0 );
        return Math.toIntExact(time);
    }

    /**
     * Sets the queue this player is waiting for
     *
     * @param queue New queue to wait in
     */
    public void setQueue(Queue queue) {
        this.queue = queue;
        if(queue != null) {
            this.queueEnterTime = LocalDateTime.now();
        }
    }

    @Override
    public int hashCode() {
        int prime = 31;
        return prime * handle.hashCode()
                + prime * priority;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof QueuedPlayer)) {
            return false;
        }
        QueuedPlayer other = (QueuedPlayer) o;
        return other.getHandle().equals(handle)
                && other.priority == priority;
    }
}
