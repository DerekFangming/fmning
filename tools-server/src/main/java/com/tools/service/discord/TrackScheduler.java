package com.tools.service.discord;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.tools.dto.YoutubeTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<YoutubeTrack> queue;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
    }

    public void queue(YoutubeTrack youtubeTrack) {
        if (!player.startTrack(youtubeTrack.getTrack(), true)) {
        }

        queue.offer(youtubeTrack);
    }

    public void nextTrack() {
        player.stopTrack();
        queue.poll();
        YoutubeTrack youtubeTrack = queue.peek();
        if (youtubeTrack != null) {
            player.startTrack(youtubeTrack.getTrack(), false);
        }
    }

    public void stop() {
        player.stopTrack();
        queue.clear();
    }

    public List<YoutubeTrack> getQueue() {
        return new ArrayList<>(queue);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            nextTrack();
        }
    }
}