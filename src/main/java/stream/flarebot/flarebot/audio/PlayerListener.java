package stream.flarebot.flarebot.audio;

import com.arsenarsen.lavaplayerbridge.player.Player;
import com.arsenarsen.lavaplayerbridge.player.Track;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import stream.flarebot.flarebot.DataHandler;
import stream.flarebot.flarebot.Getters;
import stream.flarebot.flarebot.commands.music.*;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.util.MessageUtils;
import stream.flarebot.flarebot.util.general.FormatUtils;
import stream.flarebot.flarebot.util.general.GuildUtils;
import stream.flarebot.flarebot.util.votes.VoteUtil;

import java.util.Queue;

public class PlayerListener extends AudioEventAdapter {

    private Player player;

    public PlayerListener(Player player) {
        this.player = player;
    }

    @Override
    public void onTrackEnd(AudioPlayer aplayer, AudioTrack atrack, AudioTrackEndReason reason) {
        GuildWrapper wrapper = DataHandler.getGuild(Long.parseLong(player.getGuildId()));

        if (wrapper == null) return;

        VoteUtil.remove(SkipCommand.getSkipUUID(), wrapper.getGuild());

        if (wrapper.isSongnickEnabled()) {
            if (GuildUtils.canChangeNick(player.getGuildId())) {
                Guild c = wrapper.getGuild();
                if (c == null) {
                    wrapper.setSongnick(false);
                } else {
                    if (player.getPlaylist().isEmpty())
                        c.getController().setNickname(c.getSelfMember(), null).queue();
                }
            } else {
                if (!GuildUtils.canChangeNick(player.getGuildId())) {
                    MessageUtils.sendPM(Getters.getGuildById(player.getGuildId()).getOwner().getUser(),
                            "FlareBot can't change it's nickname so SongNick has been disabled!");
                }
            }
        }
    }

    @Override
    public void onTrackStart(AudioPlayer aplayer, AudioTrack atrack) {

        GuildWrapper wrapper = DataHandler.getGuild(Long.parseLong(player.getGuildId()));
        if (wrapper.getMusicAnnounceChannelId() != null) {
            TextChannel c = Getters.getChannelById(wrapper.getMusicAnnounceChannelId());
            if (c != null) {
                if (c.getGuild().getSelfMember().hasPermission(c,
                        Permission.MESSAGE_EMBED_LINKS,
                        Permission.MESSAGE_READ,
                        Permission.MESSAGE_WRITE)) {
                    Track track = player.getPlayingTrack();
                    Queue<Track> playlist = player.getPlaylist();
                    c.sendMessage(MessageUtils.getEmbed()
                            .addField("Now Playing", SongCommand.getLink(track), false)
                            .addField("Duration", FormatUtils
                                    .formatDuration(track.getTrack().getDuration()), false)
                            .addField("Requested by",
                                    String.format("<@!%s>", track.getMeta()
                                            .get("requester")), false)
                            .addField("Next up", playlist.isEmpty() ? "Nothing" :
                                    SongCommand.getLink(playlist.peek()), false)
                            .setImage("https://img.youtube.com/vi/" + track.getTrack().getIdentifier() + "/hqdefault.jpg")
                            .build()).queue();
                } else {
                    wrapper.setMusicAnnounceChannelId(null);
                }
            } else {
                wrapper.setMusicAnnounceChannelId(null);
            }
        }
        if (wrapper.isSongnickEnabled()) {
            Guild c = wrapper.getGuild();
            if (c == null || !GuildUtils.canChangeNick(player.getGuildId())) {
                if (!GuildUtils.canChangeNick(player.getGuildId())) {
                    wrapper.setSongnick(false);
                    MessageUtils.sendPM(wrapper.getGuild().getOwner().getUser(),
                            "FlareBot can't change it's nickname so SongNick has been disabled!");
                }
            } else {
                Track track = player.getPlayingTrack();
                String str = null;
                if (track != null) {
                    str = track.getTrack().getInfo().title;
                    if (str.length() > 32)
                        str = str.substring(0, 32);
                    str = str.substring(0, str.lastIndexOf(' ') + 1);
                } // Even I couldn't make this a one-liner
                c.getController()
                        .setNickname(c.getSelfMember(), str)
                        .queue();
            }
        }
    }
}
