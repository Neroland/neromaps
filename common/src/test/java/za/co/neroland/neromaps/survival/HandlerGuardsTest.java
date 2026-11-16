package za.co.neroland.neromaps.survival;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Guards on serverbound handlers that need a live server to call, checked in their source: a signpost edit makes
 * vanilla's build checks first, and a revoke that removed nothing touches nobody.
 */
class HandlerGuardsTest {

    static String body(String code, String signature) {
        int a = code.indexOf(signature);
        assertTrue(a >= 0, signature);
        int open = code.indexOf('{', a);
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return code.substring(open, i + 1);
            }
        }
        throw new AssertionError("unbalanced " + signature);
    }

    @Test
    void signpostEditsNeedBuildPermissionAtThatPosition() {
        String code = IdentityFreeTest.stripComments(RepoFiles.read(RepoFiles.java().resolve("survival/SurvivalHandlers.java")));
        String edit = body(code, "public static void signpostEdit(");
        int guard = edit.indexOf("mayEdit(p, pos)");
        int write = edit.indexOf("be.edit(");
        assertTrue(guard > 0 && guard < write, "the permission check runs before the signpost is written");
        String may = body(code, "static boolean mayEdit(");
        assertTrue(may.contains("!p.isSpectator()"), "spectators may not edit");
        assertTrue(may.contains("p.mayBuild()"), "adventure mode may not edit");
        assertTrue(may.contains("mayInteract(p, pos)"), "spawn protection and claims apply");
    }

    @Test
    void aRevokeThatRemovedNothingTouchesNobody() {
        String code = IdentityFreeTest.stripComments(RepoFiles.read(RepoFiles.java().resolve("server/ServerHandlers.java")));
        String revoke = body(code, "public static int revoke(");
        int empty = revoke.indexOf("if (gone.isEmpty()) {");
        int after = revoke.indexOf("afterRevoke(");
        assertTrue(empty > 0 && empty < after, "zero grants returns before afterRevoke");
        String branch = revoke.substring(empty, revoke.indexOf('}', empty));
        assertTrue(branch.contains("return 0;"));
    }
}
