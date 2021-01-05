package com.tools.service.discord;

import com.tools.ToolsProperties;
import com.tools.dto.DiscordObjectDto;
import com.tools.repository.DiscordGuildRepo;
import com.tools.repository.DiscordUserRepo;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DiscordService extends BaseEventListener {

    private final DiscordGuildRepo discordGuildRepo;
    private final DiscordUserRepo discordUserRepo;
    private final JDA jda;
    private final ToolsProperties toolsProperties;

    @Scheduled(cron = "0 0 4 * * *")
    public void announceBirthDay() {
        try {
            announceBirthday(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void announceBirthday(boolean mentionEveryone) {
        discordGuildRepo.findById(toolsProperties.getDcDefaultGuildId()).ifPresent(g -> {
            if (g.isBirthdayEnabled()) {
                Guild guild = jda.getGuildById(g.getId());
                MessageChannel channel = guild.getTextChannelById(g.getBirthdayChannelId());

                Calendar today = Calendar.getInstance();
                String birthday = String.format("%02d-%02d", today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH));
                discordUserRepo.findByBirthday(birthday).forEach(d -> {
                    String message = replacePlaceHolder(g.getBirthdayMessage(), d.getName(), d.getId());
                    channel.sendMessage(mentionEveryone ? "@here " + message : message).queue();

                    try {
                        Role r = jda.getRoleById(g.getBirthdayRoleId());
                        guild.addRoleToMember(d.getId(), jda.getRoleById(g.getBirthdayRoleId())).queue();
                    } catch (Exception ignored){
                        ignored.printStackTrace();
                    }
                });

                Calendar yesterday = Calendar.getInstance();
                yesterday.add(Calendar.DAY_OF_MONTH, -1);
                String passedBirthday = String.format("%02d-%02d", yesterday.get(Calendar.MONTH) + 1, yesterday.get(Calendar.DAY_OF_MONTH));
                discordUserRepo.findByBirthday(passedBirthday).forEach(d -> {
                    try {
                        guild.removeRoleFromMember(d.getId(), jda.getRoleById(g.getBirthdayRoleId())).queue();
                    } catch (Exception ignored){}
                });
            }
        });
    }

    public List<DiscordObjectDto> getTextChannels(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            return guild.getTextChannels()
                    .stream()
                    .map(tc -> DiscordObjectDto.builder()
                            .id(tc.getId())
                            .name(tc.getName())
                            .build())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    public List<DiscordObjectDto> getRoles(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            return guild.getRoles()
                    .stream()
                    .map(r -> DiscordObjectDto.builder()
                            .id(r.getId())
                            .name(r.getName() )
                            .build())
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }


}