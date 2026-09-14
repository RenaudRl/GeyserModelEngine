package re.imc.geysermodelengine.managers.model.propertyhandler;

import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.RenderPipeline;
import kr.toxicity.model.api.nms.ModelDisplay;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.Tracker;
import kr.toxicity.model.api.util.function.BonePredicate;
import me.zimzaza4.geyserutils.spigot.api.EntityUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;
import re.imc.geysermodelengine.managers.model.entity.EntityData;
import re.imc.geysermodelengine.util.BooleanPacker;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BetterModelPropertyHandler implements PropertyHandler {

    private static final String DEBUG_BONES_OPTION = "options.debug.bones";
    private static final int BITS_PER_PROPERTY = BooleanPacker.MAX_BOOLEANS;

    // Plage declaree par GeyserUtils pour toute propriete entiere (`GeyserUtils.MIN_VALUE` /
    // `MAX_VALUE`, ±1 000 000), alignee sur l'avertissement de Geyser `IntProperty`. Le client
    // Bedrock ecrete a cette plage : un entier au-dela ne transporte plus les bits qu'on croit.
    private static final int GEYSER_INT_PROPERTY_LIMIT = 1_000_000;

    private final GeyserModelEngine plugin;

    // Modeles dont le contrat de visibilite a deja ete journalise : une seule fois par modele,
    // sinon `updateEntityProperties` (appele a chaque tick de proprietes) noierait le journal.
    private final Set<String> loggedBoneContracts = ConcurrentHashMap.newKeySet();

    public BetterModelPropertyHandler(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public void sendScale(EntityData entityData, Collection<Player> players, float lastScale, boolean firstSend) {
        BetterModelEntityData betterModelEntityData = (BetterModelEntityData) entityData;
        Tracker tracker = (Tracker) betterModelEntityData.getModelInstance();
        ModelScaler scaler = tracker.scaler();
        var scale = scaler.scale(tracker);
        players.forEach(player -> EntityUtils.sendCustomScale(player, betterModelEntityData.getEntity().getEntityId(), scale));
    }

    @Override
    public void sendColor(EntityData entityData, Collection<Player> players, Color lastColor, boolean firstSend) {
        if (players.isEmpty()) return;

        BetterModelEntityData betterModelEntityData = (BetterModelEntityData) entityData;

        Color color = new Color(0xFFFFFF);
        if (betterModelEntityData.isHurt()) color = new Color(betterModelEntityData.getEntityTracker().damageTintValue());

        if (firstSend) {
            if (color.equals(lastColor)) return;
        }

        for (Player player : players) {
            EntityUtils.sendCustomColor(player, betterModelEntityData.getEntity().getEntityId(), color);
        }

        betterModelEntityData.setHurt(false);
    }

    @Override
    public void sendHitBox(EntityData entityData, Player player) {
        BetterModelEntityData betterModelEntityData = (BetterModelEntityData) entityData;
        float w = 0;
        EntityUtils.sendCustomHitBox(player, betterModelEntityData.getEntity().getEntityId(), 0.02f, w);
    }

    @Override
    public void updateEntityProperties(EntityData entityData, Collection<Player> players, boolean firstSend, String... forceAnims) {
        BetterModelEntityData model = (BetterModelEntityData) entityData;

        int entity = model.getEntity().getEntityId();

        Map<String, Boolean> boneUpdates = new HashMap<>();
        Map<String, Boolean> animUpdates = new HashMap<>();
        Set<String> anims = new HashSet<>();

        // Trace du parcours des os, uniquement pour la premiere publication d'un modele quand
        // `options.debug.bones` est actif. `null` = pas de trace, aucun cout sur le chemin nominal.
        String modelName = model.getEntityTracker().name();
        boolean traceBones = plugin.getConfigManager().getConfig().getBoolean(DEBUG_BONES_OPTION)
                && !loggedBoneContracts.contains(modelName);
        List<String> trace = traceBones ? new ArrayList<>() : null;

        Collection<RenderedBone> rootBones = model.getEntityTracker().bones();
        rootBones.forEach(bone -> processBone(model, bone, boneUpdates, trace));

        RenderPipeline handler = model.getEntityTracker().getPipeline();

        for (RenderedBone renderedBone : handler.bones()) {
            if (model.getEntityTracker().bone(renderedBone.name()).runningAnimation() != null) {
                BlueprintAnimation anim = model.getEntityTracker().renderer().animations().get(renderedBone.runningAnimation().name());
                anims.add(renderedBone.runningAnimation().name());
                if (anim.override() && anim.loop() == AnimationIterator.Type.PLAY_ONCE) {
                    break;
                }
            }
        }

        for (String id : handler.getParent().animations().keySet()) {
            if (anims.contains(id)) {
                animUpdates.put(id, true);
            } else {
                animUpdates.put(id, false);
            }
        }

        Set<String> lastPlayed = new HashSet<>(model.getEntityTask().getLastPlayedAnim().asMap().keySet());

        for (Map.Entry<String, Boolean> anim : animUpdates.entrySet()) {
            if (anim.getValue()) {
                model.getEntityTask().getLastPlayedAnim().put(anim.getKey(), true);
            }
        }

        for (String anim : lastPlayed) animUpdates.put(anim, true);

        if (boneUpdates.isEmpty() && animUpdates.isEmpty()) return;

        Map<String, Integer> intUpdates = new HashMap<>();
        String namespace = plugin.getConfigManager().getConfig().getString("models.namespace");
        int i = 0;

        for (Integer integer : BooleanPacker.mapBooleansToInts(boneUpdates)) {
            intUpdates.put(namespace + ":bone" + i, integer);
            i++;
        }

        i = 0;
        for (Integer integer : BooleanPacker.mapBooleansToInts(animUpdates)) {
            intUpdates.put(namespace + ":anim" + i, integer);
            i++;
        }

        if (trace != null && loggedBoneContracts.add(modelName)) {
            logBoneContract(modelName, namespace, rootBones.size(), boneUpdates, intUpdates, trace);
        }

        if (!firstSend) {
            if (intUpdates.equals(model.getEntityTask().getLastIntSet())) {
                return;
            } else {
                model.getEntityTask().getLastIntSet().clear();
                model.getEntityTask().getLastIntSet().putAll(intUpdates);
            }
        }

        if (plugin.getConfigManager().getConfig().getBoolean("options.debug.animations")) plugin.getLogger().info(animUpdates.toString());

        players.forEach(player -> EntityUtils.sendIntProperties(player, entity, intUpdates));
    }

    public String unstripName(RenderedBone bone) {
        // **Le nom brut, et rien d'autre.** `BoneName.rawName` est deja le nom du groupe
        // Blockbench tel qu'ecrit dans le `.bbmodel`, passe en minuscules
        // (`BoneTagRegistry.parse` : `rawName = rawName.toLowerCase(Locale.ROOT)`). C'est
        // exactement le nom que porte l'os dans la geometrie generee, donc celui que
        // `RenderController` indexe cote pack.
        //
        // Le cas particulier qui reconstruisait `h_head` / `hi_head` pour un os nomme `head`
        // faisait diverger les deux listes : le pack indexait `head`, ce handler publiait
        // `h_head`. Vingt et un modeles du lot sont concernes, et une seule divergence suffit a
        // decaler tous les index qui suivent — donc a masquer les mauvais os.
        //
        // La regle n'etait pas non plus reproductible cote convertisseur : les `.bbmodel` du lot
        // nomment eux-memes `h_head` des os qui ont des enfants et `hi_head` des os qui n'en ont
        // pas, ce qui contredit le critere `getChildren().isEmpty()`.
        return bone.name().rawName();
    }

    private void processBone(BetterModelEntityData entityData, RenderedBone bone, Map<String, Boolean> map, List<String> trace) {
        String name = unstripName(bone).toLowerCase();
        if (name.equals("hitbox") || name.equals("shadow") || name.equals("mount") || name.startsWith("p_") || name.startsWith("b_") || name.startsWith("ob_")) {
            if (trace != null) trace.add("ignored " + name + " (excluded name)");
            return;
        }

        // Ce parcours est un no-op : `TRUE.children(false)` rend `TRUE`, donc `p.test(child)` est
        // toujours vrai et `processBone` n'est jamais rappele d'ici. La couverture vient de
        // `Tracker.bones()`, qui est deja la liste aplatie de tous les os (`byIdMap.values()`).
        // Conserve tel quel pour ne changer qu'une variable par cycle ; la trace le mesure.
        bone.matchTree(BonePredicate.TRUE.children(false), (child, p) -> {
            if (p.test(child)) {
                return false;
            }
            if (trace != null) trace.add("recursed-into " + unstripName(child));
            processBone(entityData, child, map, trace);
            return true;
        });

        RenderedBone activeBone = entityData.getEntityTracker().bone(bone.name());
        if (activeBone == null) activeBone = bone;

        // **Tout os doit etre publie, y compris ceux qui n'affichent rien.** L'index de chaque os
        // est sa position dans la liste TRIEE des noms, calculee des deux cotes : ici, et dans
        // `RenderController.generate` cote pack. Les deux listes doivent donc contenir exactement
        // les memes noms. Or `RenderedBone.display` vaut `null` pour un « dummyBone » — un groupe
        // sans geometrie propre, qui ne sert qu'a porter la hierarchie — et sortir ici sur ce
        // `null` retirait ces os de la liste alors que le pack, lui, les indexe.
        //
        // Mesure du 11/09/2026 sur `scene_miner_1` : 22 os indexes cote pack, 18 seulement
        // publies ici (manquaient `scene_miner_1`, `player`, `miner_hat`, `blockfx`). Tout etait
        // decale des l'index 1 — le modele ressortait ampute et sa piece optionnelle `pickaxe2`
        // restait affichee, en recevant le bit d'un autre os.
        //
        // Un os sans display n'a rien a masquer : il est publie visible.
        ModelDisplay modelDisplay = activeBone.getDisplay();

        // `ModelDisplay.invisible()` rend TRUE quand l'os est CACHE (« @return true if invisible »
        // dans BetterModel, qui ecrit lui-meme `var visible = !invisible();`). La carte publiee
        // ici doit porter la VISIBILITE : sans la negation, chaque os recoit l'inverse de son
        // etat. Le handler jumeau `ModelEnginePropertyHandler` publie bien `activeBone.isVisible()`.
        boolean visible = modelDisplay == null || !modelDisplay.invisible();

        if (trace != null) {
            trace.add("visited " + name
                    + " visible=" + visible
                    + " dummy=" + (modelDisplay == null)
                    + " trackerBone=" + (activeBone != bone)
                    + " alreadySeen=" + map.containsKey(name));
        }

        map.put(name, visible);
    }

    // Journalise, une fois par modele, exactement ce que ce handler publie : la liste triee des os
    // (le meme tri que `BooleanPacker.mapBooleansToInts`), l'index de chacun, son booleen, la
    // propriete `<namespace>:boneN` et le bit qu'il y occupe, puis les entiers envoyes. C'est la
    // moitie serveur du contrat de visibilite ; l'autre moitie est le render controller du pack.
    private void logBoneContract(String modelName, String namespace, int rootCount, Map<String, Boolean> boneUpdates,
                                 Map<String, Integer> intUpdates, List<String> trace) {
        var log = plugin.getLogger();
        List<String> sortedNames = new ArrayList<>(boneUpdates.keySet());
        Collections.sort(sortedNames);

        long visits = trace.stream().filter(line -> line.startsWith("visited ")).count();
        long ignored = trace.stream().filter(line -> line.startsWith("ignored ")).count();
        long recursed = trace.stream().filter(line -> line.startsWith("recursed-into ")).count();

        log.info("[bones] model=" + modelName + " namespace=" + namespace
                + " trackerBones=" + rootCount + " visits=" + visits + " distinct=" + sortedNames.size()
                + " ignored=" + ignored + " recursedViaMatchTree=" + recursed);
        for (String line : trace) {
            if (!line.startsWith("visited ")) log.info("[bones]   " + line);
        }
        for (String line : trace) {
            if (line.startsWith("visited ")) log.info("[bones]   " + line);
        }
        for (int index = 0; index < sortedNames.size(); index++) {
            String name = sortedNames.get(index);
            log.info("[bones]   #" + index + " " + name
                    + " visible=" + boneUpdates.get(name)
                    + " property=" + namespace + ":bone" + (index / BITS_PER_PROPERTY)
                    + " bit=" + (index % BITS_PER_PROPERTY));
        }
        intUpdates.entrySet().stream()
                .filter(entry -> entry.getKey().contains(":bone"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    int value = entry.getValue();
                    boolean inRange = Math.abs(value) <= GEYSER_INT_PROPERTY_LIMIT;
                    log.info("[bones]   int " + entry.getKey() + "=" + value + (inRange ? "" : " OUT_OF_RANGE"));
                    if (!inRange) {
                        log.warning("[bones] " + entry.getKey() + "=" + value + " exceeds the ±" + GEYSER_INT_PROPERTY_LIMIT
                                + " range declared by GeyserUtils: the Bedrock client will clamp it and the packed bits are lost.");
                    }
                });
    }
}
