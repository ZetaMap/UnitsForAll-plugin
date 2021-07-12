import static arc.Core.settings;
import static mindustry.Vars.content;

import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;

import mindustry.core.NetClient;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.UnitType;

import util.Players;


public class PluginMain extends mindustry.mod.Plugin {
	private static ObjectMap<Team, Integer> stock = new ObjectMap<>();
	private static ObjectMap<Team, Boolean> inVote = new ObjectMap<>();
	private static ObjectMap<Team, Integer> votes = new ObjectMap<>();
	private static ObjectMap<Team, Votes> sessions = new ObjectMap<>();
	private static ObjectMap<Team, Votes> cooldowns = new ObjectMap<>();
	private static ObjectMap<Team, UnitType> unit = new ObjectMap<>();
	private static Seq<String> voted = new Seq<>();
	private static double ratio = 0.6;
	private static int time = 30, duration = 2, cooldown = 5;
	private static boolean isActivated = true, timer = true;
	
	//Called after all plugins have been created and commands have been registered
	public void init() {
		//init content for all teams
		for (Team team : Team.baseTeams) {
			stock.put(team, 0);
			inVote.put(team, false); 
			votes.put(team, 0);
		}
		if (!unit.isEmpty()) unit.clear();
		if (!voted.isEmpty()) voted.clear();
		sessions.forEach(s -> s.value.stopVotes());
		if (!sessions.isEmpty()) sessions.clear();
	}
	
    public PluginMain() {
    	loadSettings(); //check if have a save for active or not clients commands and other settings

    	Events.on(EventType.WorldLoadEvent.class, e -> startTimer()); //restart the timer during a new card
    	Events.on(EventType.GameOverEvent.class, e -> {
    		init();//re-init content team
    		timer = false; //stop the timer
    	}); 
    }
	
	//register commands that run on the server
	@Override
	public void registerServerCommands(CommandHandler handler){
		handler.register("unitsforall", "[on|off]", "Active/Désactive le plugin (redémarrage du serveur nécessaire)", arg -> {
			if (arg.length == 1) {
				boolean last = isActivated;
				switch (arg[0]) {
					case "on": case "true":
						isActivated = true;
						Log.info("Plugin activé.");
						break;
					case "off": case "false":
						isActivated = false;
						Log.info("Plugin désactivé.");
						break;
					default:
						Log.err("Arguments invalides !");
				}
				saveSettings();
				if (last != isActivated) Log.info("Des changements au niveau du plugin ont été apporté. Veuillez redémarrer le serveur pour que cela prenne effet.");
				
			} else Log.info("Le plugin est actuellement " + (isActivated ? "activé" : "désactivé"));
		});
	}
	    
	//register commands that player can invoke in-game
	@Override
	public void registerClientCommands(CommandHandler handler){
		if (isActivated) {
			handler.<Player>register("unit", "<oui|non>", "Donner son avis si le spawn doit avoir lieu ou pas", (arg, player) -> {
				if (!inVote.get(player.team())) {
					Players.err(player, "Aucun vote n'a été lancé !");
					return;
				}
				if (voted.contains(player.uuid())) {
					Players.err(player, "Vous avez déjà voté !");
					return;
				}
				
				boolean accept;
				switch (arg[0]) {
					case "yes": case "y": case "oui": case "o":
						votes.put(player.team(), votes.get(player.team())+1);
						accept = true;
						break;
					case "no": case "n": case "non":
						votes.put(player.team(), votes.get(player.team())-1);
						accept = false;
						break;
					
					default:
						Players.err(player, "Arguments invalides !");
						return;
				}
				
				voted.add(player.uuid());
				Seq<Player> list = new Seq<>();
				Groups.player.each(p -> {
					if (p.team().equals(player.team())) list.add(p);
				});
				int rest = arc.math.Mathf.ceil((float) ratio * list.size);
				
				Players.messageToTeam(player.team(), "%s[orange] a voté %s le spawn de [white]%s %s [lightgray](%s votes manquants)", 
					NetClient.colorizeName(player.id, player.name), accept ? "[green]pour[]" : "[scarlet]contre[]", stock, 
					unit.get(player.team()).name, rest-votes.get(player.team()));

				if (votes.get(player.team()) < rest) return;
				Players.messageToTeam(player.team(), "[green]Vote terminé. Les unités vont spawn à coté d'un noyau.");
				clearVotes(player.team());
				startSpawn(stock.get(player.team()), unit.get(player.team()));
				cooldowns.put(player.team(), new Votes(player.team(), true));
			});
			
			handler.<Player>register("votespawn", "<unité>", "Lance un vote pour faire spawn toute les unités en stock", (arg, player) -> {
				if (stock.get(player.team()) == 0) {
					Players.err(player, "Le vote ne peut pas commencé car votre stock d'unité est vide !");
					return;
				} 
				if (inVote.get(player.team())) {
					Players.err(player, "Un vote est déjà en cours !");
					return;
				}
				if (cooldowns.get(player.team()) != null) {
					Players.err(player, "Veuillez attendre encore [green]" + createDate(cooldowns.get(player.team()).getTime()) + "[] avant de relancer un vote.");
					return;
				}
				
				UnitType search = content.units().find(b -> b.name.equals(arg[0]));
					
				if (search == null) {
					Players.err(player, "Cette unité n'existe pas! []Unités disponible :");
					player.sendMessage(content.units().toString("[scarlet], []"));
					
				} else {
					voted.add(player.uuid());
					votes.put(player.team(), votes.get(player.team())+1);
					inVote.put(player.team(), true);
					unit.put(player.team(), search);
					sessions.put(player.team(), new Votes(player.team()));
						
					Players.messageToTeam(player.team(), "%s[orange] a lancé un vote pour le spawn de [white]%s %s [lightgray]", 
						NetClient.colorizeName(player.id, player.name), stock.get(player.team()), search.name);
				}
				
			});
		
			handler.<Player>register("plugin", "<help|commande> [valeur]", "[scarlet][[Admin][] Configuration du plugin", (arg, player) -> {
				if (!Players.adminCheck(player)) return;
				
				StringBuilder builder = new StringBuilder();
				switch (arg[0]) {
					default: Players.err(player, "Arguments invalides ! []Arguments possibles :");
					case "help":
						builder.append(" [orange]- help[lightgray] : Affiche l'aide des arguments possibles.\n");
						builder.append(" [orange]- settime [white]<minutes>[lightgray] : Redéfini le temps avant nouvelle unité. (min: 1, max: 1440)\n");
						builder.append(" [orange]- setduration [white]<minutes>[lightgray] : Redéfini la durée d'un vote. (min: 1, max: 60)\n");
						builder.append(" [orange]- setcooldown [white]<minutes>[lightgray] : Redéfini le temps d'attente entre les votes. (min: 1, max: 60)\n");
						builder.append(" [orange]- forcespawn [white]<unité>[lightgray] : Fait spawn toutes les unités en stock de votre team sans passer par un vote.\n");
						builder.append(" [orange]- resetstock [white][[team][lightgray] : Réinitialise le stock d'unité d'une team ou toutes si pas d'argument.\n");
						builder.append(" [orange]- resetvotes [white][[team][lightgray] : Réinitialise les votes d'une team ou toutes si pas d'argument.\n");
						builder.append(" [orange]- resettime[lightgray] : Réinitialise le temps avant nouvelle unité.\n");
						builder.append(" [orange]- delcooldown [white][[team][lightgray] : Supprime le temps d'attente entre les votes, d'une team ou toutes si pas d'argument.\n");
						builder.append(" [orange]- default[lightgray] : Remet toutes les valeurs par défaut.\n");
						builder.append(" [orange]- info[lightgray] : Affiche les informations de la partie en cours et du plugin.\n");
						player.sendMessage(builder.toString());
						break;
					
					case "settime":
						if (arg.length == 2) {
							if (arc.util.Strings.canParseInt(arg[1])) {
								int valeur = Integer.parseInt(arg[1]);
								
								if (valeur < 1 || valeur > 1440) Players.err(player, "La valeur doit être comprise entre 1 (1min) et 1440 (1j).");
								else if (valeur < duration) Players.err(player, "Le temps avant nouvelle unité doit être supérieur à la durée d'un vote (" + createDate(duration*60) + ")");
								else {
									time = valeur;
									timer = false;
									Players.announce(player, "[green]Le temps avant nouvelle unité a été redéfifni à [scarlet]" + createDate(time*60));
									startTimer();
									saveSettings();
								}
							} else Players.err(player, "La valeur doit être un chiffre !");
						} else Players.err(player, "Veuillez entrer une valeur !");
						break;
						
					case "setduration":
						if (arg.length == 2) {
							if (arc.util.Strings.canParseInt(arg[1])) {
								int valeur = Integer.parseInt(arg[1]);
								
								if (valeur < 1 || valeur > 60) Players.err(player, "La valeur doit être comprise entre 1 (1min) et 60 (1h).");
								else if (valeur > time) Players.err(player, "La durée d'un vote doit être inférieur au temps avant nouvelle unité (" + createDate(time*60) + ")");
								else {
									duration = valeur;
									timer = false;
									Players.announce(player, "[green]La durée d'un vote a été redéfini à [scarlet]" + createDate(duration*60));
									startTimer();
									saveSettings();
								}
							} else Players.err(player, "La valeur doit être un chiffre !");
						} else Players.err(player, "Veuillez entrer une valeur !");
						break;
						
					case "setcooldown":
						if (arg.length == 2) {
							if (arc.util.Strings.canParseInt(arg[1])) {
								int valeur = Integer.parseInt(arg[1]);
								
								if (valeur < 1 || valeur > 60) Players.err(player, "La valeur doit être comprise entre 1 (1min) et 60 (1h).");
								else {
									cooldown = valeur;
									timer = false;
									Players.announce(player, "[green]Le temps d'attente entre les votes a été redéfini à [scarlet]" + createDate(cooldown*60));
									startTimer();
									saveSettings();
								}
							} else Players.err(player, "La valeur doit être un chiffre !");
						} else Players.err(player, "Veuillez entrer une valeur !");
						break;
					
					case "forcespawn":
						if (arg.length == 2) {
							UnitType search = content.units().find(b -> b.name.equals(arg[0]));
							
							if (search == null) {
								Players.err(player, "Cette unité n'existe pas! []Unités disponible :");
								player.sendMessage(content.units().toString("[scarlet], []"));
							} else {
								startSpawn(stock.get(player.team()), search);
								clearVotes(player.team());
								Players.messageToTeam(player.team(), "%s [orange]a forcé le spawn de [white] %s %s", player.name, stock.get(player.team()), unit.get(player.team()).name);
							}
						} else Players.err(player, "Veuillez entrer une valeur !");
						break;
					
					case "resetstock":
						if (arg.length == 2) {
							if (!Players.testGamemode()) {
								Players.err(player, "Indisponible dans ce mode de jeu !");
								return;
							}
							Team target = Players.findTeam(arg[1], player);
							if (target == null) return;
							stock.put(target, 0);
							Players.messageToTeam(target, "[orange]Votre stock d'unité a été réinitialisé. [lightgray](par " + player.name + "[lightgray])");
							
						} else {
							for (Team team : Team.baseTeams) stock.put(team, 0);
							Players.announce(player, "Le stock d'unité " + (Players.testGamemode() ? "de toutes les teams " : "") + "a été réinitialisé.");
						}
						break;
						
					case "resetvotes":
						if (arg.length == 2) {
							if (!Players.testGamemode()) {
								Players.err(player, "Indisponible dans ce mode de jeu !");
								return;
							}
							Team target = Players.findTeam(arg[1], player);
							if (target == null) return;
							
							if (!inVote.get(target)) Players.err(player, "Cette team n'est pas en vote");
							else {
								sessions.get(target).stopVotes();
								sessions.remove(target);
								clearVotes(target);
								Players.messageToTeam(target, "[orange]Tous vos votes ont été réinitialisés. [lightgray](par " + player.name + "[lightgray])");
							}

						} else {
							for (Team team : Team.baseTeams) {
								votes.put(team, 0);
								inVote.put(team, false);
							}
							voted.clear();
							sessions.forEach(t -> t.value.stopVotes());
							sessions.clear();
							Players.announce(player, "Les votes " + (Players.testGamemode() ? "de toutes les teams " : "") + "ont été réinitialisés.");
						}
						break;
					
					case "resettime":
						timer = false;
						Players.announce(player, "Le temps avant nouvelle unité a été réinitialisé.");
						startTimer();
						break;
						
					case "delcooldown":
						if (arg.length == 2) {
							if (!Players.testGamemode()) {
								Players.err(player, "Indisponible dans ce mode de jeu !");
								return;
							}
							Team target = Players.findTeam(arg[1], player);
							if (target == null) return;
							
							Votes c = cooldowns.get(player.team());
							if (c == null) Players.err(player, "Cette team n'a pas de temps d'attente !");
							else {
								c.stopVotes();
								cooldowns.remove(player.team());
								Players.messageToTeam(target, "[orange]Votre temps d'attente entre les votes a été réinitialisé. [lightgray](par " + player.name + "[lightgray])");
							}

						} else {
							cooldowns.forEach(t -> t.value.stopVotes());
							cooldowns.clear();
							Players.announce(player, "Le temps d'attente entre les votes " + (Players.testGamemode() ? "de toutes les teams " : "") + "a été réinitialisé.");
						}
						break;
					
					case "default":
						init();
						timer = false;
						time = 30;
						duration = 2;
						cooldown = 5;
						saveSettings();
						Players.announce(player, "Le plugin a été entierement réinitialisé.");
						startTimer();
						break;
					
					case "info":
						Seq<String> temp = new Seq<>();
						voted.each(v -> temp.add(Players.findByID(v).player.name));
						builder.append("[orange]Joueurs qui ont votés : []" + (temp.isEmpty() ? "<empty>" : temp.toString("[white], ")));
						if (Players.testGamemode()) {
							for (Team team : Team.baseTeams) {
								builder.append("- [royal]" + team.name + " :[] [green]" + stock.get(team) + "[] unité(s) en stock | En Vote : [green]" 
										+ (inVote.get(team) ? "oui[] | Nombre de vote : [green]" + votes.get(team) : "non") 
										+ "[] | Unité choisi : [green]" + (unit.get(player.team()) != null ? unit.get(team).name : "<unknown>"));
							}
							builder.append("\n[orange]Informations des teams :[]\n" + temp.toString("\n[white]"));
						} else 
							builder.append("\n[orange]Informations de la partie :[]\n"
									+ "- [green]" + stock.get(player.team()) + "[] unité(s) en stock | En Vote : [green]" 
									+ (inVote.get(player.team()) ? "oui[] | Nombre de vote : [green]" + votes.get(player.team()) : "non") 
									+ "[] | Unité choisi : [green]" + (unit.get(player.team()) != null ? unit.get(player.team()).name : "<unknown>"));
						
						mindustry.mod.Mods.LoadedMod mod = mindustry.Vars.mods.getMod("unitsforall-plugin");
						builder.append("\n[orange]Informations du plugin :[white]"
								+ "\n- Nom : " + mod.meta.displayName()
								+ "\n- Version : " + mod.meta.version
								+ "\n- Auteur : " + mod.meta.author
								+ "\n- Chemin : " + mod.file.path()
								+ "\n- Description : " + mod.meta.description);
						Call.infoMessage(player.con, builder.toString());
						break;
				}
			});
		}
	}
	
	private void loadSettings() {
		if (settings.has("UnitForAll")) {
			try {
				String[] save = settings.getString("UnitForAll").split(" \\| ");
				isActivated = Boolean.parseBoolean(save[0]);
				time = Integer.parseInt(save[1]);
				duration = Integer.parseInt(save[2]);
				cooldown = Integer.parseInt(save[3]);
			} catch (Exception e) {
				saveSettings();
				loadSettings();
			};
			
		} else {
			saveSettings();
			loadSettings();
		}
	}
	
	private void saveSettings() {
		settings.put("UnitForAll", String.join(" | ", isActivated+"", time+"", duration+"", cooldown+""));
		settings.forceSave();
	}
	
	private void startSpawn(int count, UnitType unit) {
		//#################################################################################
	}
	
	private void startTimer() {
		if (isActivated) {
			new Thread() {
				@SuppressWarnings("static-access")
				public void run() {
					try {
						Thread.sleep(2000);
						timer = true;
						String text = "[orange]Unités en stock : [green]%s[]\nTemps avant nouvelle\nunité : [green]%s[]%s";
						int sec = time*60;
				
						while (timer) {
							if (sec-- <= 0) {
								for (Team team : Team.baseTeams) stock.put(team, stock.get(team)+1);
								Call.announce("[green]Nouvelle unité en stock !");
								sec = time*60;
							}
							
							final String restTime = createDate(sec);
							Groups.player.each(p -> Call.infoPopup(p.con, String.format(text, 
									stock.get(p.team()), restTime, (sessions.get(p.team()) != null ? 
										"\nTemps de vote restant :\n [green]" + createDate(sessions.get(p.team()).timeLeft) 
										+ "\n[]Unité choisi : [green]" + unit.get(p.team()).name
										+ "\n[]Nombre de vote : [green]" + votes.get(p.team()) 
										: cooldowns.get(p.team()) != null ? "\nProchain vote possible\ndans : [green]" + createDate(cooldowns.get(p.team()).getTime())
									: "")), 
								(float) 1.002, 17, 0, 0, 0, 0)
							);
							Thread.sleep(1000);
						}
					} catch (InterruptedException e) { e.printStackTrace(); }
				}
			}.start();
		}	
	}
	
	private String createDate(int sec) {
		int h = sec / 60 / 60 % 24;
		int m = sec / 60 % 60;
		int s = sec % 60;
		String output = "";
		  
		if (h != 0) output += h + "h ";
		if (m != 0) output += m + "min ";
		if (s != 0) output += s + "sec ";
		if (h == 0 && m == 0 && s == 0) output += "0sec ";
		  
		return output.strip();
	}
	
	private static void clearVotes(Team team) {
		if (sessions.containsKey(team)) {
			sessions.get(team).stopVotes();
			sessions.remove(team);
		}
		votes.put(team, 0);
		inVote.put(team, false);
		unit.remove(team);
		Groups.player.each(p -> p.team().equals(team), p -> {
			if (voted.contains(p.uuid())) voted.remove(p.uuid());
		});
	}
	
	 
	public static class Votes {
		private Thread task = null;
		private static int timeLeft;
		
		public Votes(Team team) { new Votes(team, false); }
		public Votes(Team team, boolean inCooldown) {
			if (inCooldown) timeLeft = cooldown*60;
			else timeLeft = duration*60;
			
			this.task = new Thread("Votes") {
				public void run() {
					while (timeLeft > 0) {
						try {
							timeLeft--;
							Thread.sleep(1000);
						} catch (InterruptedException e) {}
					}
					if (!inCooldown) {
						Players.messageToTeam(team, "[scarlet]Temps écoulé ! Le vote pour faire spawn [accent]" + stock.get(team) + " " + unit.get(team).name + "[] est annulé.");
						clearVotes(team);
						cooldowns.put(team, new Votes(team, true));
					} else {
						Players.messageToTeam(team, "[green]Temps d'attente terminé ! Vous pouvez à nouveau lancer un vote.");
						cooldowns.remove(team);
					}
				}
			};
			this.task.start();
		}
		
		public void stopVotes() {
			if (this.task == null || this.task.isInterrupted()) return;
			this.task.interrupt();
		}
		
		public Integer getTime() {
			
			if (this.task == null || this.task.isInterrupted()) return null;
			else return timeLeft;
		}
	}
}