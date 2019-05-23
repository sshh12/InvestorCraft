package io.sshh.investorcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class InvestorCraft extends JavaPlugin {

    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ = null;
    private static Permission perms = null;
    private static Chat chat = null;
    private static PriceAPI priceAPI = null;

    @Override
    public void onDisable() {
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            log.severe(String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        setupPermissions();
        setupChat();
        loadConfig();
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
            chat = rsp.getProvider();
        }
        return chat != null;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = rsp.getProvider();
        return perms != null;
    }

    public void loadConfig() {
        getConfig().options().copyDefaults(true);
        priceAPI = new PriceAPI(getConfig().getString("investing.alphavantagekey"));
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {

        if (!(sender instanceof Player)) {
            log.info("Only players are supported for this Example Plugin, but you should not do this!!!");
            return true;
        }
        
        if(!commandLabel.equals("invest")) {
            return false;
        }

        Player player = (Player) sender;
        
        if(args.length == 3) {
            
            String symbol = args[1].toUpperCase();
            int amt = Integer.parseInt(args[2]);
            
            if(symbol.length() == 0 || amt <= 0) {
                sender.sendMessage("Invalid parameters.");
                return true;
            }
            
            double pricePer = (int)(priceAPI.getPrice(symbol) * 1000 * 100) / 100;
            double priceOverall = pricePer * amt;
            
            if(priceOverall <= 0) {
                sender.sendMessage("Invalid Purchase.");
                return true;
            }
            
            EconomyResponse er;
            if(args[0].equals("buy")) {
                er = econ.withdrawPlayer(player, priceOverall);
                if(er.transactionSuccess() && modifyShares(player, symbol, amt))
                    sender.sendMessage(String.format("You have purchased %d share(s) of %s, for %s.", amt, symbol, econ.format(priceOverall)));
            } else if(args[0].equals("sell")) {
                if(modifyShares(player, symbol, -amt)) {
                    er = econ.depositPlayer(player, priceOverall);
                    if(er.transactionSuccess())
                        sender.sendMessage(String.format("You have sold %d share(s) of %s, for %s.", amt, symbol, econ.format(priceOverall)));
                }
            }
            
            return true;
        
        } else if(args.length == 2 && args[0].equals("price")) {
            
            String symbol = args[1].toUpperCase();
            double price = (int)(priceAPI.getPrice(symbol) * 1000 * 100) / 100;
            if(price > 0) {
                sender.sendMessage(String.format("%s is %s per share.", symbol, econ.format(price)));
            } else {
                sender.sendMessage("Error fetching stock.");
            }
            return true;
            
        }

        return false;

    }
    
    private boolean modifyShares(Player player, String symbol, double diff) {
        FileConfiguration config = getConfig();
        String path = String.format("accounts.%s.%s", player.getUniqueId(), symbol);
        config.addDefault(path, 0);
        double newValue = config.getDouble(path) + diff;
        if(diff > 0 || (diff < 0 && newValue > 0)) {
            config.set(path, newValue);
            saveConfig();
            return true;
        }
        return false;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public static Permission getPermissions() {
        return perms;
    }

    public static Chat getChat() {
        return chat;
    }

}

class PriceAPI {
    
    private final static String BASE_URL = "https://www.alphavantage.co/";
    private String key;

    public PriceAPI(String apiKey) {
        key = apiKey;
    }

    private JsonObject fetch(String urlString) {
        try {
            URL url = new URL(urlString);
            URLConnection request = url.openConnection();
            request.connect();
            JsonParser jp = new JsonParser();
            JsonElement root = jp.parse(new InputStreamReader((InputStream) request.getContent()));
            JsonObject rootObj = root.getAsJsonObject();
            return rootObj;
        } catch (MalformedURLException ex) {
            return null;
        } catch (IOException ex) {
            return null;
        }
    }

    public double getPrice(String symbol) {
        String url = String.format("%squery?function=GLOBAL_QUOTE&symbol=%s&apikey=%s", BASE_URL, symbol, key);
        JsonElement data = fetch(url).getAsJsonObject("Global Quote").get("05. price");
        if(data.isJsonNull()) return 0.0;
        return data.getAsDouble();
    }

}
