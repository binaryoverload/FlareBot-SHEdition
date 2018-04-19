package stream.flarebot.flarebot.mod.nino;

import com.google.common.collect.ImmutableList;
import net.dv8tion.jda.core.entities.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.flarebot.flarebot.DataHandler;
import stream.flarebot.flarebot.objects.GuildWrapper;
import stream.flarebot.flarebot.objects.NINO;
import stream.flarebot.flarebot.util.Pair;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

public class URLChecker {

    private static final Logger logger = LoggerFactory.getLogger(URLChecker.class);

    private static final ThreadGroup GROUP = new ThreadGroup("URLChecker");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r ->
            new Thread(GROUP, r, "Checker-" + GROUP.activeCount()));

    private static final ConcurrentHashMap<UUID, Byte> redirects = new ConcurrentHashMap<>();

    private static URLChecker instance;

    public static URLChecker instance() {
        if (instance == null)
            instance = new URLChecker();
        return instance;
    }

    public void checkMessage(GuildWrapper wrapper, TextChannel channel, String message,
                             BiConsumer<URLCheckFlag, String> callback) {
        NINO nino = wrapper.getNINO();
        Matcher m = nino.getNINOMode() == NINOMode.AGGRESSIVE ? URLConstants.URL_PATTERN_NO_PROTOCOL.matcher(message)
                : URLConstants.URL_PATTERN.matcher(message);
        if (!m.find()) return;

        String url = normalizeUrl(m.group(0));

        EXECUTOR.submit(() -> {
            Set<URLCheckFlag> flags = wrapper.getNINO().getURLFlags();

            Pair<URLCheckFlag, String> pair = checkURL(url, flags, channel);

            if (pair != null) {
                // Returned if it's a whitelisted URL
                if (pair.getKey() == null && pair.getValue() != null) {
                    callback.accept(null, null);
                    return;
                }
                logger.debug("{} was found to be under the flag {} ({})", url, pair.getKey(), pair.getValue());
                callback.accept(pair.getKey(), pair.getValue());
                return;
            } else {
                if (nino.getNINOMode() == NINOMode.RELAXED) return; // Shouldn't follow.
                logger.debug("{} was not flagged, going to try and follow the URL.", url);
                if ((pair = followURL(url, channel, flags, null)) != null) {
                    logger.debug("{} was found to be under the flag {} ({}) after following it", url, pair.getKey(), pair.getValue());
                    callback.accept(pair.getKey(), pair.getValue());
                    return;
                }
            }

            callback.accept(null, null);
        });
    }

    private Pair<URLCheckFlag, String> checkURL(String url, Set<URLCheckFlag> flags, TextChannel channel) {
        Matcher matcher;
        logger.debug("Checking {} with flags: {}", url, Arrays.toString(flags.toArray()));
        // Check whitelisted domains
        if ((matcher = URLConstants.WHITELISTED_DOMAINS_PATTERN.matcher(url)).find()) {
            return new Pair<>(null, matcher.group());
        }

        // Blacklist
        // I may want to implement this in the future but right now I don't think it's needed.

        // IP Grabber
        if (flags.contains(URLCheckFlag.IP_GRABBER)) {
            logger.debug(URLConstants.IP_GRABBER_PATTERN.toString());
            if ((matcher = URLConstants.IP_GRABBER_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.IP_GRABBER, matcher.group());
            }
        }

        // Discord Invite
        if (flags.contains(URLCheckFlag.DISCORD_INVITE)) {
            if ((matcher = URLConstants.DISCORD_INVITE_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.DISCORD_INVITE, matcher.group());
            }
        }

        // Phishing
        if (flags.contains(URLCheckFlag.PHISHING)) {
            if ((matcher = URLConstants.PHISHING_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.PHISHING, matcher.group());
            }
        }

        // Suspicious TLDs
        if (flags.contains(URLCheckFlag.SUSPICIOUS)) {
            if ((matcher = URLConstants.SUSPICIOUS_TLDS_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.SUSPICIOUS, matcher.group());
            }
        }

        // Screamers
        if (flags.contains(URLCheckFlag.SCREAMERS)) {
            if ((matcher = URLConstants.SCREAMERS_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.SCREAMERS, matcher.group());
            }
        }

        // NSFW
        if (flags.contains(URLCheckFlag.NSFW) && channel != null && !channel.isNSFW() && !URLConstants.NSFW.isEmpty()) {
            if ((matcher = URLConstants.NSFW_PATTERN.matcher(url)).find()) {
                return new Pair<>(URLCheckFlag.NSFW, matcher.group());
            }
        }

        // URL
        if (flags.contains(URLCheckFlag.URL)) {
            return new Pair<>(URLCheckFlag.URL, url);
        }

        return null;
    }

    private Pair<URLCheckFlag, String> followURL(String url, TextChannel channel, Set<URLCheckFlag> flags, UUID uuid) {
        UUID redirectUUID = uuid;
        // Have a fallback so we don't follow a redirect loop forever.
        if (uuid != null) {
            if (redirects.containsKey(uuid) && redirects.get(uuid) == 10)
                return null;
            else // Something weird happened, let's just handle it as it should be handled.
                redirects.put(uuid, (byte) 1);
        } else
            redirectUUID = UUID.randomUUID();

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.connect();

            int resp = connection.getResponseCode();
            String location = connection.getHeaderField("Location");

            if (location != null) {
                logger.info("{} ({}) wants to redirect to {}", url, resp, location);

                Pair<URLCheckFlag, String> pair = checkURL(location, flags, channel);
                if (pair != null)
                    return pair;

                redirects.put(redirectUUID, (byte) (uuid != null ? redirects.get(uuid) + 1 : 1));
                return followURL(location, channel, flags, redirectUUID);
            } else
                return null;
        } catch (IOException e) {
            logger.warn("Failed to follow URL! URL: " + url + ", Message: " + e.getMessage());
            return null;
        }
    }

    private String normalizeUrl(String url) {
        String normalized = url;
        if (!url.startsWith("http")) {
            normalized = "http://" + url;
        }

        return normalized.trim();
    }

    public void runTests() {
        List<String> tests = ImmutableList.of(
                "Check this out https://i.go.iplogger.com/12dxzfcs.php",
                "http://cool.webcam",
                "http://www.discord.gg/b1nzy",
                "https://flarebot.stream",
                //"http://bit.ly/2Ix3h5k",
                "www.iplogger.com",
                "http://test.iplogger.com/t?=192.0.0.1",
                "http://bit.ly/2FY9rJW"
        );

        GuildWrapper wrapper = new GuildWrapper(1L);
        wrapper.getNINO().addURLFlags(URLCheckFlag.IP_GRABBER, /*URLCheckFlag.BLACKLISTED,*/ URLCheckFlag.DISCORD_INVITE,
                URLCheckFlag.PHISHING, URLCheckFlag.SUSPICIOUS);

        for (String url : tests) {
            instance().checkMessage(wrapper, null, url, (flag, u) -> logger.info(url + " - " + flag));
        }
    }

    public void runTests(String[] links, TextChannel channel) {
        GuildWrapper wrapper = DataHandler.getGuild(channel.getGuild().getIdLong());

        byte before = wrapper.getNINO().getMode();
        Set<URLCheckFlag> oldFlags = wrapper.getNINO().getURLFlags();
        wrapper.getNINO().setMode(NINOMode.AGGRESSIVE.getMode());
        wrapper.getNINO().setFlags(URLCheckFlag.getDebugFlags());

        StringBuilder sb = new StringBuilder();
        sb.append("Links:\n");
        CountDownLatch countDownLatch = new CountDownLatch(links.length);
        for (String link : links) {
            instance().checkMessage(wrapper, null, link.trim(), (flag, u) -> {
                if (flag != null)
                    sb.append(link.trim()).append(" - ").append(flag).append("\n");
                logger.info("{} - {}", link.trim(), flag);
                countDownLatch.countDown();
            });
        }
        try {
            countDownLatch.await();

            channel.sendMessage(sb.toString()).queue();
        } catch (InterruptedException e) {
            logger.error("Failed to wait for checks", e);
            channel.sendMessage("Something went wrong: " + e.getMessage()).queue();
        }
        wrapper.getNINO().setMode(before);
        wrapper.getNINO().setFlags(oldFlags);
    }
}
