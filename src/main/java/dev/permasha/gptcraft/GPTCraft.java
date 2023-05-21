package dev.permasha.gptcraft;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import dev.permasha.gptcraft.commands.AskCommand;
import dev.permasha.gptcraft.commands.ListconversationsCommand;
import dev.permasha.gptcraft.commands.NextconversationCommand;
import dev.permasha.gptcraft.commands.SetconversationCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GPTCraft extends JavaPlugin implements Listener {

    private final static ExecutorService executor;
    public static OpenAiService service;
    public static HashMap<Player, List<List<ChatMessage>>> conversations = new HashMap<>();
    public static HashMap<Player, Integer> conversationIndexMap = new HashMap<>();

    @Override
    public void onEnable() {
        // Plugin startup logic

        saveDefaultConfig();
        String token = getConfig().getString("token");

        getCommand("ask").setExecutor(new AskCommand());
        getCommand("listconversations").setExecutor(new ListconversationsCommand());
        getCommand("nextconversation").setExecutor(new NextconversationCommand());
        getCommand("setconversation").setExecutor(new SetconversationCommand());

        getServer().getPluginManager().registerEvents(this, this);

        service = new OpenAiService(token);
        CompletionRequest completionRequest = CompletionRequest.builder()
                .prompt("Somebody once told me the world is gonna roll me")
                .model("ada")
                .echo(true)
                .build();
        service.createCompletion(completionRequest).getChoices().forEach(System.out::println);
    }

    static {
        executor = Executors.newFixedThreadPool(1);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

    }

    public static HashMap<Player, Integer> getConversationIndexMap() {
        return conversationIndexMap;
    }

    public static HashMap<Player, List<List<ChatMessage>>> getConversations() {
        return conversations;
    }

    public static void setConversationIndex(Player player, int index) {
        if(index >= 0 && index < conversations.get(player).size()) {
            conversationIndexMap.put(player, index);
        }
    }

    public static boolean nextConversation(Player player) {
        if(conversationIndexMap.get(player) != null && conversationIndexMap.get(player) < conversations.get(player).size() - 1) {
            Integer finInt = conversationIndexMap.get(player) + 1;
            conversationIndexMap.put(player, finInt);
            return false;
        }
        conversations.put(player, new ArrayList<>());
        conversations.get(player).add(new ArrayList<>());
        conversationIndexMap.put(player, conversations.get(player).size() - 1);

        conversations.get(player).get(conversationIndexMap.get(player)).add(new ChatMessage("system", "Context: You are an AI assistant in the game Minecraft. Limit your responses to 256 characters. Assume the player cannot access commands unless explicitly asked for them. Do not simulate conversations"));
        return true;
    }

    private static void askSync(Player player, String question) {
        if(conversations.get(player) == null || conversations.get(player).size() == 0) {
            nextConversation(player);
        }
        List<ChatMessage> conversation = conversations.get(player).get(conversationIndexMap.get(player));
        conversation.add(new ChatMessage("user", question));
        ChatCompletionRequest req = ChatCompletionRequest.builder()
                .messages(conversation)
                .model("gpt-3.5-turbo")
                .build();
        ChatMessage reply;
        if(player == null) return;
        try {
            reply = service.createChatCompletion(req).getChoices().get(0).getMessage();
            conversation.add(reply);
            if(conversation.size() > 10) {
                conversation.remove(1); // don't remove the first message, as it's the minecraft context
            }
            player.sendMessage("<ChatGPT> " + reply.getContent().replaceAll("^\\s+|\\s+$", ""));
        } catch (RuntimeException e) {
            Bukkit.getLogger().info("Error while communicating with OpenAI - " + e);
            if(e.getMessage().toLowerCase().contains("exceeded your current quota")) {
//                player.sendMessage(Text.translatable("mcgpt.ask.quota").setStyle(Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://platform.openai.com/account/usage")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of("https://platform.openai.com/account/usage")))));
            } else {
//                player.sendMessage(Text.translatable("mcgpt.ask.error").setStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.of(e.getMessage())))));
            }
        }
    }

    public static String ask(Player player, String question) {

        executor.execute(() -> {
            try {
                askSync(player, question);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return question;
    }
    public OpenAiService getService() {
        return service;
    }

    @EventHandler
    public void onTake(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if (event.getClickedBlock().getType().equals(Material.LECTERN)) {
                if (event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.WRITABLE_BOOK)) {
                    Lectern lectern = (Lectern) event.getClickedBlock().getState();

                    ItemStack book = player.getInventory().getItemInMainHand();
                    BookMeta bookMeta = (BookMeta) book.getItemMeta();
                    String question = bookMeta.getPages().get(0);
                    ask(player, question);
                    lectern.getInventory().setItem(0, null);
                    event.setCancelled(true);
                }
            }
        }
    }

}
