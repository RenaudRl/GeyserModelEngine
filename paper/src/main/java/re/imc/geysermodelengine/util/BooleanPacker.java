package re.imc.geysermodelengine.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class BooleanPacker {

    // Nombre de booleens par propriete entiere Bedrock. GeyserUtils declare chaque propriete sur
    // [-1 000 000 ; 1 000 000] (plage recommandee par Geyser `IntProperty`) et le client ecrete a
    // cette plage : 2^19 - 1 = 524 287 tient, 2^20 - 1 = 1 048 575 ne tient plus. A 24 bits, tout
    // modele dont un os visible a l'index >= 20 perdait la valeur entiere entiere (mesure du
    // 14/09/2026 : `scene_miner_1`, 22 os, 4 194 303 ecrete a 1 000 000, 7 os visibles sur 22).
    // Doit rester egal a `BooleanPacker.MAX_BOOLEANS` du module Geyser, qui genere le pack.
    public static final int MAX_BOOLEANS = 19;

    public static int booleansToInt(List<Boolean> booleans) {
        int result = 0;
        int i = 1;

        for (boolean b : booleans) {
            if (b) {
                result += i;
            }
            i *= 2;
        }

        return result;
    }

    public static int mapBooleansToInt(Map<String, Boolean> booleanMap) {
        int result = 0;
        int i = 1;

        List<String> keys = new ArrayList<>(booleanMap.keySet());
        Collections.sort(keys);

        for (String key : keys) {
            if (booleanMap.get(key)) {
                result += i;
            }
            i *= 2;
        }
        return result;
    }

    public static List<Integer> booleansToInts(List<Boolean> booleans) {
        List<Integer> results = new ArrayList<>();
        int result = 0;
        int i = 1;
        int i1 = 1;

        for (boolean b : booleans) {
            if (b) {
                result += i;
            }
            if (i1 % MAX_BOOLEANS == 0 || i1 == booleans.size()) {
                results.add(result);
                result = 0;
                i = 1;
            } else {
                i *= 2;
            }
            i1++;
        }

        return results;
    }

    public static List<Integer> mapBooleansToInts(Map<String, Boolean> booleanMap) {
        List<String> keys = new ArrayList<>(booleanMap.keySet());
        List<Boolean> booleans = new ArrayList<>();

        Collections.sort(keys);

        for (String key : keys) {
            booleans.add(booleanMap.get(key));
        }
        return booleansToInts(booleans);
    }
}
