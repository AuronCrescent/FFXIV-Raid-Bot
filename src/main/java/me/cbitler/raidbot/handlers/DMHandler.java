package me.cbitler.raidbot.handlers;

import me.cbitler.raidbot.RaidBot;
import me.cbitler.raidbot.creation.CreationStep;
import me.cbitler.raidbot.logs.LogParser;
import me.cbitler.raidbot.raids.PendingRaid;
import me.cbitler.raidbot.raids.RaidManager;
import me.cbitler.raidbot.selection.SelectionStep;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * Handle direct messages sent to the bot
 * @author Christopher Bitler
 */
public class DMHandler extends ListenerAdapter {
    RaidBot bot;

    /**
     * Create a new direct message handler with the parent bot
     * @param bot The parent bot
     */
    public DMHandler(RaidBot bot) {
        this.bot = bot;
    }

    /**
     * Handle receiving a private message.
     * This checks to see if the user is currently going through the raid creation process or
     * the role selection process and acts accordingly.
     * @param e The private message event
     */
    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent e) {
        User author = e.getAuthor();

        if (bot.getCreationMap().containsKey(author.getId())) {
            if(e.getMessage().getRawContent().equalsIgnoreCase("cancel")) {
                bot.getCreationMap().remove(author.getId());

                if(bot.getPendingRaids().get(author.getId()) != null) {
                    bot.getPendingRaids().remove(author.getId());
                }
                e.getChannel().sendMessage("OK, on abandonne la création du raid, tristesse ...").queue();
                return;
            }

            CreationStep step = bot.getCreationMap().get(author.getId());
            boolean done = step.handleDM(e);

            // If this step is done, move onto the next one or finish
            if (done) {
                CreationStep nextStep = step.getNextStep();
                if(nextStep != null) {
                    bot.getCreationMap().put(author.getId(), nextStep);
                    e.getChannel().sendMessage(nextStep.getStepText()).queue();
                } else {
                    //Create raid
                    bot.getCreationMap().remove(author.getId());
                    PendingRaid raid = bot.getPendingRaids().remove(author.getId());
                    try {
                        RaidManager.createRaid(raid);
                        e.getChannel().sendMessage("\\o/ Le raid a bien été créé !").queue();
                    } catch (Exception exception) {
                        e.getChannel().sendMessage("ALERTE ! J'ai pas pu créé le raid, je suis visiblement persécuté et je n'ai pas le droit d'écrire dans le chan ?").queue();
                    }
                }
            }
        } else if (bot.getRoleSelectionMap().containsKey(author.getId())) {
            if(e.getMessage().getRawContent().equalsIgnoreCase("cancel")) {
                bot.getRoleSelectionMap().remove(author.getId());
                e.getChannel().sendMessage("Ok, on abandonne la sélection des rôles.").queue();
                return;
            }
            SelectionStep step = bot.getRoleSelectionMap().get(author.getId());
            boolean done = step.handleDM(e);

            //If this step is done, move onto the next one or finish
            if(done) {
                SelectionStep nextStep = step.getNextStep();
                if(nextStep != null) {
                    bot.getRoleSelectionMap().put(author.getId(), nextStep);
                    e.getChannel().sendMessage(nextStep.getStepText()).queue();
                } else {
                    // We don't need to handle adding to the raid here, that's done in the pickrolestep
                    bot.getRoleSelectionMap().remove(author.getId());
                }
            }

        }

        if(e.getMessage().getAttachments().size() > 0 && e.getChannelType() == ChannelType.PRIVATE) {
            for(Message.Attachment attachment : e.getMessage().getAttachments()) {
                System.out.println(attachment.getFileName());
                if(attachment.getFileName().endsWith(".evtc") || attachment.getFileName().endsWith(".evtc.zip")) {
                    new Thread(new LogParser(e.getChannel(), attachment)).start();
                }
            }
        }
    }
}
