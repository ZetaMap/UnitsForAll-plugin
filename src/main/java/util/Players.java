package util;

import arc.struct.Seq;
import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;


public class Players {
	public final Player player;
	public final String find;
	public final String[] rest;
	
	private Players(Player p, String args, String s) {
		Seq<String> temp = new Seq<String>().addAll(args.split(" "));
		if (temp.size > 1 && temp.get(0).isBlank()) temp.remove(0);
		
		this.player = p;
		this.find = s;
		this.rest = temp.toArray(String.class);
	}
	
	public static void err(Player player, String fmt, Object... msg) {
    	player.sendMessage("[scarlet]Erreur : " + String.format(fmt, msg));
    }
    public static void info(Player player, String fmt, Object... msg) {
    	player.sendMessage("Info : " + String.format(fmt, msg));
    }
    public static void warn(Player player, String fmt, Object... msg) {
    	player.sendMessage("[gold]Attention : []" + String.format(fmt, msg));
    }
    public static void messageToTeam(mindustry.game.Team team, String fmt, Object... msg) {
    	if (testGamemode())
    		Call.sendMessage(String.format("[#%s]%s: []" + fmt, 
    			team.color.toString().substring(2), team.name.toUpperCase(), msg));
    	else Call.sendMessage(String.format(fmt, msg));
    }
    
	public static boolean testGamemode() {
		mindustry.game.Rules mode = mindustry.Vars.state.rules;
		return mode.pvp || mode.infiniteResources;
	}
    
    //check the player if admin 
    public static boolean adminCheck(Player player) {
    	if(!player.admin()){
    		player.sendMessage("[scarlet]Cette commande est uniquement pour les admins!");
            return false;
    	} else return true;
    }
    
    public static Team findTeam(String name, Player pDefault) {
		switch (name) {
    		case "sharded": return Team.sharded;
    		case "blue": return Team.blue;
    		case "crux": return Team.crux;
    		case "derelict": return Team.derelict;
    		case "green": return Team.green;
    		case "purple": return Team.purple;
    		default:
    			err(pDefault, "Cette team n'existe pas ! []Teams disponibles :");
    			Seq <String> teamList = new Seq<>();
    			for (Team team : Team.baseTeams) teamList.add(team.name);
    			pDefault.sendMessage(teamList.toString("[scarlet], []"));
    			return null;
		}
    }
    
    public static Players findByName(String args) {
    	final String arg= args + " ";
    	Player target = Groups.player.find(p -> arg.startsWith(p.name + " "));
    	if (target == null) target = Groups.player.find(p -> arg.startsWith(Strings.stripColors(p.name) + " "));
    	
    	if (target == null) return new Players(target, arg, "");
    	else return new Players(target, arg.substring(Strings.stripColors(target.name).length()), arg.substring(0, arg.length()-Strings.stripColors(target.name).length()));
    }
    
    public static Players findByID(String args) {
    	final String arg= args + " ";
    	Player target = Groups.player.find(p -> arg.startsWith(p.uuid() + " "));
    	
    	if (target == null) return new Players(target, arg, "");
    	else return new Players(target, arg.substring(target.uuid().length()), arg.substring(0, arg.length()-target.uuid().length()));
    }
    
    public static Players findByNameOrID(String str) {
    	Players target = Players.findByName(str);
    	if (target == null) target = Players.findByID(str);
    	
    	return target;
    }
}