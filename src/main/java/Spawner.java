import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

import java.util.Random;

import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;


public class Spawner {
	private static int unitLimit;
	public SpawnResult type;
	public int result;
	
	private Spawner(SpawnResult t, int r) {
		this.type = t;
		this.result = r;
	}
	
    public static Spawner spawn(Team team, UnitType unit, int count) { 
    	return spawn(team, unit, count, 0, unitLimit(team.cores())); 
    }
    
    private static Spawner spawn(Team team, UnitType unit, int count, int inside, int unitLimit) {
    	mindustry.world.blocks.storage.CoreBlock.CoreBuild core = team.cores().get(new Random().nextInt(team.cores().size));
    	
    	short rest = (short) count, steps = 0;
    	
    	if(unit.flying || unit.groundLayer == 75.0f) {
    		float spread = 40f / 1.5f;

    		for(int i = 0; i < count; i++) {
    			if (unit.create(team).count() >= unitLimit) return new Spawner(SpawnResult.unitLimit, rest);
    			else {
    				rest--;
    				Unit unit1 = unit.create(team);
    				unit1.set(core.x + Mathf.range(spread), core.y + Mathf.range(spread));
    				spawnEffect(unit1);
    			}
    		}
    	
    	} else if (unit.id > 24) {
    		Tmp.v1.set(core).limit(40 + core.block.size * tilesize/2 * Mathf.sqrt2);
    		int x, y;
    		
			for(int i = 0; i < count; i++) {
    			Tmp.v1.rnd(tilesize*core.block.size*(1+inside/2)*1.2f);
    			x = Mathf.round((core.x + Tmp.v1.x)/tilesize);
    			y = Mathf.round((core.y + Tmp.v1.y)/tilesize);
    			Unit unit1 = unit.create(team);
    			
    			if (unit1.count() >= unitLimit) {
    				unit1.remove();
    				return new Spawner(SpawnResult.unitLimit, rest);
    			} else if (!world.solid(x, y) && world.floor(x, y).isLiquid) {
    				rest--;
    				unit1.set(core.x + Tmp.v1.x, core.y + Tmp.v1.y);
    				spawnEffect(unit1);     
    			} else {
    				i--;
    				if (steps++ > 180) {
    					if (inside++ > 25) return new Spawner(SpawnResult.noLiquid, rest);
    					else return spawn(team, unit, rest, inside++, unitLimit);
    				}
    			}
    		}
    		
    	} else {
    		Tmp.v1.set(core).limit(40 + core.block.size * tilesize/2 * Mathf.sqrt2);
    		
    		for(int i = 0; i < count; i++) {
    			Tmp.v1.rnd(tilesize*core.block.size*(1+inside/2)*1.2f);
    			Unit unit1 = unit.create(team);

    			if (unit1.count() >= unitLimit) {
    				unit1.remove();
    				return new Spawner(SpawnResult.unitLimit, rest);
    			} else if (!world.solid(Mathf.round((core.x + Tmp.v1.x)/tilesize), Mathf.round((core.y + Tmp.v1.y)/tilesize))) {
    				rest--;
    				unit1.set(core.x + Tmp.v1.x, core.y + Tmp.v1.y);
    				spawnEffect(unit1);
    			} else {
    				i--;
    				if (steps++ > 180) {
    					if (inside++ > 20) return new Spawner(SpawnResult.noPlace, rest);
    					else return spawn(team, unit, rest, inside++, unitLimit);
    				}
    			}
    		}
    	}
    	return new Spawner(SpawnResult.succes, -1);
    }

    private static void spawnEffect(Unit unit){
        unit.rotation = unit.angleTo(world.width()/2f * tilesize, world.height()/2f * tilesize);
        unit.apply(mindustry.content.StatusEffects.unmoving, 30f);
        unit.add();

        mindustry.gen.Call.spawnEffect(unit.x, unit.y, unit.rotation, unit.type);
    }
    
    private static int unitLimit(arc.struct.Seq<mindustry.world.blocks.storage.CoreBlock.CoreBuild> cores) {
    	unitLimit = 0;
    	
    	cores.each(c -> {
    		if (c.block == Blocks.coreShard) unitLimit+=8;
    		if (c.block == Blocks.coreFoundation) unitLimit+=16;
    		if (c.block == Blocks.coreNucleus) unitLimit+=24;
    	});
    	return unitLimit;
    }
    
    
    public static enum SpawnResult {
    	succes,
    	noLiquid,
    	noPlace,
    	unitLimit
    }
}
