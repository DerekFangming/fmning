package com.tools.service.discord;

import com.tools.ToolsProperties;
import com.tools.domain.DiscordUser;
import com.tools.repository.DiscordGuildRepo;
import com.tools.repository.DiscordUserRepo;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class MessageReceivedEventListener extends BaseEventListener {

    private final HttpClient httpClient;
    private final DiscordGuildRepo discordGuildRepo;
    private final DiscordUserRepo discordUserRepo;
    private final ToolsProperties toolsProperties;

    private Pattern userMentionPattern = Pattern.compile("<@.*?>");
    private Pattern birthdayPattern = Pattern.compile("([0-9][0-9])-([0-3][0-9])");
    private ArrayList<String> nbList = new ArrayList<String>() {{
        add(" 可太牛逼了");
        add(" 真是帅炸了");
        add(" tql tql tql");
        add(" 带带我带带我");
        add(" 真会玩");
        add(" 沃日这波无敌啊");
        add(" 大腿带带我");
        add(" 还有这种操作，学到了学到了");
        add(" 6666666666 很骚");
        add(" 哇 好帅啊");
        add(" 大佬 tql");
        add(" 太对了 太对了");
        add(" 有内味儿了");
        add(" 有点东西");
        add(" 对面就这？就这？");
        add(" 美汁汁儿啊！");
    }};

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {

//        event.getMember().getUser().isBot()?   TODO

        // ==========================================================

        try {
            //Message message = event.getMessage();
            String content = event.getMessage().getContentRaw();
            if (!content.toLowerCase().startsWith("yf")) return;

            String[] command = content.split("\\s+");

            MessageChannel channel = event.getChannel();
            Member member = event.getMember();
            if (member == null) return;

            if ("help".equalsIgnoreCase(command[1])) {
                channel.sendMessage(new EmbedBuilder()
                        .setTitle("妖风电竞 bot指令")
                        .setDescription("**绑定Origin ID：**`yf apex link ID`\n将指令里的`ID`替换成你的Origin ID即可。" +
                                "只需绑定一次，绑定之后，每次使用yf指令组队，都将查询你的战绩。可以多次运行这个指令来修改或者更新你的Discord帐号对应的Origin ID。\n\n" +
                                "**发送组队邀请：**`yf apex 你想说的`\n指令里`你想说的`可以随意输入，比如2=1。它将出现在组队邀请的标题中。" +
                                "如果使用这条指令的时候你在妖风电竞的某个语音频道中，组队邀请会自带你当前的语音频道链接，方便其他玩家点击进入。\n\n" +
                                "**太牛了：**`yf nb @某人`\n被@的人太强了！如果要夸自己，可以省略@， 直接使用`yf nb`\n\n" +
                                "**注册生日：**`yf birthday MM-DD`\n注册你的生日。注册后生日当天会在生日频道得到祝福以及专属Tag。\n\n" +
                                "**取消生日提醒：**`yf birthday disable`\n\n" +
                                "**全部注册的生日：**`yf birthday`\n\n" +
                                "**本月过生日的成员：**`yf birthday month`")
                        .build()).queue();
            } else if ("apex".equalsIgnoreCase(command[1])) {
                if (command.length > 3 && "link".equalsIgnoreCase(command[2])) {
                    DiscordUser discordUser = discordUserRepo.findById(member.getId())
                            .orElse(DiscordUser.builder().id(member.getId()).name(member.getUser().getName()).guildId(member.getGuild().getId()).build());
                    discordUser.setApexId(command[3]);
                    discordUserRepo.save(discordUser);

                    channel.sendMessage("<@" + discordUser.getId() + "> 你已绑定Origin ID: **" + discordUser.getApexId() + "**").queue();
                    return;
                }
                String extras = null;
                String kills = "";
                String rankName = "";
                String rankAvatar = "";
                String inviteUrl = null;

                if (command.length >= 3) {
                    String[] extrasArray = Arrays.copyOfRange(command, 1, command.length);
                    extras = String.join(" ", extrasArray);
                }

                DiscordUser discordUser = discordUserRepo.findById(member.getId()).orElse(null);
                if (discordUser == null || discordUser.getApexId() == null) {
                    channel.sendMessage("<@" + member.getId() + "> 你未绑定Origin ID。运行yf help查看如何绑定。").queue();
                    return;
                }

                HttpGet httpGet = new HttpGet("https://public-api.tracker.gg/v2/apex/standard/profile/origin/" + discordUser.getApexId());
                httpGet.setHeader("TRN-Api-Key", "0721ec03-b743-40ff-97fa-0d04568f655a");
                HttpResponse response = httpClient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404) {
                    channel.sendMessage("<@" + discordUser.getId() + "> 你绑定的Origin ID **" + discordUser.getApexId() +
                            "** 不存在，请重新绑定。你可以尝试在 https://apex.tracker.gg 上搜索你的ID。你的Origin ID是加好友是输入的" +
                            "ID，不是登录Origin的用户名。").queue();
                    return;
                } else if (status != 200) {
                    channel.sendMessage("<@" + discordUser.getId() + "> 无法查询").queue();
                    return;
                }
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(responseBody);

                JSONArray segments = json.getJSONObject("data").getJSONArray("segments");
                for (int i = 0; i < segments.length(); i++) {
                    JSONObject segment = segments.getJSONObject(i);
                    if (segment.getString("type").equals("overview")) {
                        JSONObject stats = segment.getJSONObject("stats");
                        if (stats.has("kills")) {
                            kills = stats.getJSONObject("kills").getString("displayValue");
                        } else {
                            kills = "无法读取";
                        }

                        JSONObject rankScore = stats.getJSONObject("rankScore");
                        rankName = rankScore.getJSONObject("metadata").getString("rankName");
                        rankAvatar = rankScore.getJSONObject("metadata").getString("iconUrl");
                        break;
                    }
                }

                GuildVoiceState voiceState = member.getVoiceState();
                if (voiceState != null) {
                    VoiceChannel voiceChannel = voiceState.getChannel();
                    if (voiceChannel != null) {
                        Invite invite = voiceChannel.createInvite().complete();
                        inviteUrl = invite.getUrl();
                    }
                }

                channel.sendMessage(new EmbedBuilder()
                        .setAuthor(member.getEffectiveName() + " 请求Apex组队", null, member.getUser().getAvatarUrl())
                        .setThumbnail(rankAvatar)
                        .setTitle(extras)
                        .setDescription(inviteUrl == null ? inviteUrl : "[:race_car: 点此上车 :race_car:](" + inviteUrl + ")")
                        .addField("Origin ID", discordUser.getApexId(), true)
                        .addField("段位", rankName, true)
                        .addField("击杀", kills, true)
                        .build()).queue();



            } else if ("birthday".equalsIgnoreCase(command[1])) {
                if (command.length == 2) {
                    List<DiscordUser> users = discordUserRepo.findByBirthdayNotNullOrderByBirthdayAsc();
                    if (users.size() == 0) {
                        channel.sendMessage("**尚未有人注册生日**").queue();
                    } else {
                        channel.sendMessage("**全部已注册的生日**\n\n" + users.stream().map(u -> "**" + u.getBirthday() + ":** " + u.getName())
                                .collect(Collectors.joining("\n"))).queue();
                    }
                } else {
                    if (command[2].equalsIgnoreCase("month")) {
                        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
                        if (command.length == 4) {
                            try {
                                int givenMonth = Integer.parseInt(command[3]);
                                if (givenMonth > 0 && givenMonth < 13) month = givenMonth;
                            } catch (Exception ignored){}
                        }
                        List<DiscordUser> users = discordUserRepo.findByBirthdayStartingWithOrderByBirthdayAsc(String.format("%02d", month));
                        if (users.size() == 0) {
                            channel.sendMessage("**" + month + "月尚未有人注册生日**").queue();
                        } else {
                            channel.sendMessage("**" + month + "月已注册的生日**\n\n" + users.stream().map(u -> "**" + u.getBirthday() + ":** " + u.getName())
                                    .collect(Collectors.joining("\n"))).queue();
                        }
                    } else if (command[2].equalsIgnoreCase("disable")) {
                        discordUserRepo.findById(member.getId()).ifPresent(u -> {
                            u.setBirthday(null);
                            discordUserRepo.save(u);
                            channel.sendMessage("<@" + member.getId() + "> 成功取消生日提醒").queue();
                        });
                    } else {
                        Matcher m = birthdayPattern.matcher(command[2]);
                        if (m.find()) {
                            int month = Integer.parseInt(m.group(1));
                            int day = Integer.parseInt(m.group(2));
                            if (month > 0 && month < 13 && day > 0 && day < 32) {
                                DiscordUser discordUser = discordUserRepo.findById(member.getId())
                                        .orElse(DiscordUser.builder().id(member.getId()).name(member.getUser().getName()).guildId(member.getGuild().getId()).build());
                                discordUser.setBirthday(command[2]);
                                discordUserRepo.save(discordUser);
                                channel.sendMessage("<@" + member.getId() + "> 成功注册生日为**" + month + "月" + day + "日**。" ).queue();
                                return;
                            }

                        }
                        channel.sendMessage("<@" + member.getId() + "> 无法识别" + command[2] +
                                "。生日格式必须为**月份-日期**， 比如**01-02** 或者 **11-29**").queue();
                    }
                }
            } else if ("ping".equalsIgnoreCase(command[1])) {
                channel.sendMessage("Bot operational. Latency " + event.getJDA().getGatewayPing() + " ms").queue();
            } else if ("nb".equalsIgnoreCase(command[1])) {
                Matcher matcher = userMentionPattern.matcher(content);
                String mention = null;
                while (matcher.find()) {mention= matcher.group(0);}
                channel.sendMessage((mention == null ? ("<@" + member.getId() + ">") : mention) +
                        nbList.get(new Random().nextInt(nbList.size()))).queue();
            } else if ("yygq".equalsIgnoreCase(command[1])) {
                channel.sendMessage("<@" + member.getId() + "> 警告！ 本DC禁止阴阳怪气！").queue();
            } else if ("debug".equalsIgnoreCase(command[1])) {
//                throw new IllegalStateException("asdkjahsdj");
//                announceBirthday(false, event.getJDA());
            } else {
                channel.sendMessage("<@" + member.getId() + "> 无法识别指令 **" + content + "**。请运行yf help查看指令说明。").queue();
            }
        } catch (Exception e) {
            logError(event, discordGuildRepo, e);
        }
    }


}
