package me.minebuilders.clearlag.entities;

import me.minebuilders.clearlag.Util;
import me.minebuilders.clearlag.entities.attributes.*;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author bob7l
 */
public class AttributeParser {

    public Result getAttributesFromString(EntityType t, StringTokenizer tk) {

        final Result result = new Result();

        while (tk.hasMoreTokens()) {

            EntityAttribute<Entity> attribute = null;

            String tok = tk.nextToken();

            boolean reversed = (tok.startsWith("!"));

            if (reversed)
                tok = tok.substring(1);

            if (tok.startsWith("name=\"") || tok.startsWith("hasMeta=\"")) {

                StringBuilder name = new StringBuilder();

                name.append(tok, 6, (tok.endsWith("\"") ? tok.length() - 1 : tok.length()));

                while (tk.hasMoreTokens()) {

                    tok = tk.nextToken();

                    if (tok.endsWith("\"")) {
                        name.append(" ").append(tok, 0, tok.length() - 1);
                        break;
                    }
                    name.append(" ").append(tok);
                }

                if (tok.startsWith("name=\""))
                    attribute = new EntityNameAttribute(name.toString());
                else
                    attribute = new EntityHasMetaAttribute(name.toString());

            } else if (tok.startsWith("hasName"))
                attribute = new EntityHasNameAttribute();
            else if (tok.startsWith("liveTime="))
                attribute = new EntityLifeLimitAttribute(Integer.parseInt(tok.substring(9)));
            else if (tok.startsWith("isMounted"))
                attribute = new EntityMountedAttribute();
            else if (tok.startsWith("onGround"))
                attribute = new EntityOnGroundAttribute();
            else if (tok.startsWith("id=") || tok.startsWith("material=")) {

                if (t == EntityType.ITEM) {

                    String input = tok.substring(tok.startsWith("id=") ? 3 : 9); // Adjust substring based on prefix
                    Material mat = null;

                    // First try direct lookup (case-sensitive, for enum names)
                    mat = Material.getMaterial(input.toUpperCase());

                    // If that fails, try case-insensitive match
                    if (mat == null) {
                        mat = Material.matchMaterial(input);
                    }

                    // If that still fails, try legacy name matching
                    if (mat == null) {
                        mat = Material.matchMaterial(input, true);
                    }

                    // If all lookups fail, log a warning
                    if (mat == null) {
                        Util.warning("Could not find material matching '" + input + "'. Please check your configuration.");
                    }

                    attribute = new EntityMaterialAttribute(mat);
                }
            }

            if (attribute != null) {
                attribute.setReversed(reversed);
                result.attributes.add(attribute);
            } else
                result.nonApplicables.add(tok);
        }

        return result;
    }

    public static class Result {

        private final ArrayList<EntityAttribute<Entity>> attributes = new ArrayList<>(2);

        private final List<String> nonApplicables = new ArrayList<>(1);

        public ArrayList<EntityAttribute<Entity>> getAttributes() {
            return attributes;
        }

        public List<String> getNonApplicables() {
            return nonApplicables;
        }
    }
}
