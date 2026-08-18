package com.pcdc.td.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.pcdc.td.TDGame;
import com.pcdc.td.data.UpgradeNodeDef;
import com.pcdc.td.meta.MetaEffects;
import com.pcdc.td.ui.Lang;
import com.pcdc.td.ui.Widgets;

/** Árbol de mejoras: nodos posicionados libremente con líneas de dependencia. */
public class UpgradeTreeScreen extends ScreenAdapter {

    private final float nodeW, nodeH, gapX, gapY;
    private final boolean compact = com.pcdc.td.ui.UiScale.compact();

    private final TDGame game;
    private final Stage stage = new Stage(com.pcdc.td.ui.UiScale.viewport());
    /** Iconos 3D de las torres-resultado de las recetas de fusión. */
    private final com.pcdc.td.render.TowerIconRenderer iconRenderer =
        new com.pcdc.td.render.TowerIconRenderer(96);
    private final ObjectMap<String, TextureRegion> fusionIcons = new ObjectMap<>();
    /** Scroll de la pasada anterior: al comprar se reconstruye y no debe saltar. */
    private ScrollPane scroll;

    /** true si se abrió desde el menú principal: Volver regresa allí. */
    private final boolean fromMenu;
    /**
     * Filtros del árbol: con 101 nodos, encontrar "lo que puedo comprar ya" o
     * "la rama de este tanque" era un paseo. Los que no pasan el filtro no
     * desaparecen: se atenúan, para que el mapa mental no se mueva.
     */
    private boolean onlyBuyable;
    private String branchFilter;

    public UpgradeTreeScreen(TDGame game) {
        this(game, false);
    }

    public UpgradeTreeScreen(TDGame game, boolean fromMenu) {
        this.game = game;
        this.fromMenu = fromMenu;
        // en móvil los nodos encogen: con el tamaño de escritorio solo caben 3
        this.nodeW = compact ? 140f : 190f;
        this.nodeH = compact ? 78f : 96f;
        this.gapX = compact ? 26f : 42f;
        this.gapY = compact ? 30f : 46f;
        build();
    }

    private void goBack() {
        if (fromMenu) game.setScreen(new MainMenuScreen(game));
        else game.setScreen(new LevelSelectScreen(game));
        dispose();
    }

    /** Nodo recomendado al jugador perdido ante 77 tarjetas (uno solo). */
    private String suggestedId;

    /**
     * Elige el siguiente nodo sensato: antiaéreo si no tienes ninguno, si no
     * el desbloqueo de tanque más barato alcanzable, si no el nodo más barato.
     */
    private void computeSuggested() {
        suggestedId = null;
        UpgradeNodeDef cheapestUnlock = null, cheapest = null, cheapestAffordable = null;
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (game.profile.hasUpgrade(n.id)) continue;
            boolean met = true;
            for (String req : n.requires) {
                if (!game.profile.hasUpgrade(req)) {
                    met = false;
                    break;
                }
            }
            if (!met) continue;
            boolean unlock = "unlockTower".equals(n.effect.stat);
            // sin antiaéreos, la metralleta es la sugerencia sin discusión
            if (unlock && "machinegun".equals(n.effect.value)
                && !game.stats.towerAvailable("machinegun")) {
                suggestedId = n.id;
                return;
            }
            if (unlock && (cheapestUnlock == null || n.cost < cheapestUnlock.cost)) {
                cheapestUnlock = n;
            }
            if (cheapest == null || n.cost < cheapest.cost) cheapest = n;
            if (n.cost <= game.profile.metaCurrency
                && (cheapestAffordable == null || n.cost < cheapestAffordable.cost)) {
                cheapestAffordable = n;
            }
        }
        UpgradeNodeDef pick = cheapestUnlock != null ? cheapestUnlock : cheapest;
        // una meta de ahorro cercana vale; una a más del doble del bolsillo,
        // no: mejor señalar algo que se pueda comprar hoy
        if (pick != null && cheapestAffordable != null
            && pick.cost > game.profile.metaCurrency * 2) {
            pick = cheapestAffordable;
        }
        if (pick != null) suggestedId = pick.id;
    }

    private void build() {
        float keepX = scroll != null ? scroll.getScrollX() : -1f;
        float keepY = scroll != null ? scroll.getScrollY() : -1f;
        stage.clear();
        computeSuggested();

        ObjectMap<String, float[]> layout = computeLayout();
        float maxCol = 0f, maxRow = 0f;
        for (float[] p : layout.values()) {
            maxCol = Math.max(maxCol, p[0]);
            maxRow = Math.max(maxRow, p[1]);
        }
        // franja superior para el rótulo de cada rama (el tanque de la columna)
        float headerH = compact ? 24f : 30f;
        float canvasW = (maxCol + 1f) * (nodeW + gapX) + gapX;
        float canvasH = (maxRow + 1f) * (nodeH + gapY) + gapY + headerH;

        Group canvas = new Group();
        canvas.setSize(canvasW, canvasH);

        // centros de nodo para las líneas
        ObjectMap<String, Vector2> centers = new ObjectMap<>();
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            float[] p = layout.get(n.id);
            float x = gapX + p[0] * (nodeW + gapX);
            float y = canvasH - headerH - gapY - nodeH - p[1] * (nodeH + gapY);
            centers.put(n.id, new Vector2(x + nodeW / 2f, y + nodeH / 2f));
        }

        // rótulo de columna: nombre del tanque (o del grupo) sobre la fila 0
        ObjectMap<Integer, String> headers = new ObjectMap<>();
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            float[] p = layout.get(n.id);
            if (p[1] != 0f || headers.containsKey((int) p[0])) continue;
            headers.put((int) p[0], headerLabel(n));
        }
        // progreso por columna: nodos comprados / total de la rama
        com.badlogic.gdx.utils.IntIntMap colTotal = new com.badlogic.gdx.utils.IntIntMap();
        com.badlogic.gdx.utils.IntIntMap colOwned = new com.badlogic.gdx.utils.IntIntMap();
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            float[] p = layout.get(n.id);
            if (p[1] >= fusionBandRow) continue; // las recetas cuelgan de dos ramas
            colTotal.getAndIncrement((int) p[0], 0, 1);
            if (game.profile.hasUpgrade(n.id)) colOwned.getAndIncrement((int) p[0], 0, 1);
        }
        for (ObjectMap.Entry<Integer, String> h : headers) {
            if (h.value == null) continue;
            int total = colTotal.get(h.key, 0);
            int owned = colOwned.get(h.key, 0);
            Label head = new Label(h.value + "  " + owned + "/" + total, game.skin, "gold");
            head.setFontScale(com.pcdc.td.ui.Fonts.rel(compact ? 0.8f : 0.9f));
            // tocar la cabecera aísla esa rama (y vuelve a tocarla la libera)
            final String headBranch = branchOfColumn(h.key, layout);
            head.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.enabled);
            head.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.sounds.playSfx("click");
                    branchFilter = headBranch != null && headBranch.equals(branchFilter)
                        ? null : headBranch;
                    build();
                }
            });
            head.setBounds(gapX + h.key * (nodeW + gapX), canvasH - headerH, nodeW, headerH);
            head.setAlignment(com.badlogic.gdx.utils.Align.center);
            canvas.addActor(head);
            // barrita de progreso al pie de la cabecera
            if (owned > 0 && total > 0) {
                com.badlogic.gdx.scenes.scene2d.ui.Image bar = new com.badlogic.gdx.scenes.scene2d.ui.Image(
                    new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                        game.skin.getRegion("white")));
                bar.setColor(1f, 0.82f, 0.4f, 0.65f);
                bar.setBounds(gapX + h.key * (nodeW + gapX), canvasH - headerH + 1f,
                    nodeW * owned / (float) total, 3f);
                canvas.addActor(bar);
            }
        }

        // rótulo de la banda: explica que ahí abajo están las recetas
        Label bandLabel = new Label(Lang.get("tree.head_fusions"), game.skin, "gold");
        bandLabel.setFontScale(com.pcdc.td.ui.Fonts.rel(compact ? 0.8f : 0.95f));
        bandLabel.setBounds(gapX, canvasH - headerH - gapY - nodeH
            - (fusionBandRow - 0.55f) * (nodeH + gapY), nodeW * 2f, headerH);
        canvas.addActor(bandLabel);

        // líneas de dependencia debajo de los nodos
        TextureRegion white = game.skin.getRegion("white");
        Actor lines = new Actor() {
            @Override
            public void draw(Batch batch, float parentAlpha) {
                for (UpgradeNodeDef n : game.catalogs.upgrades) {
                    Vector2 to = centers.get(n.id);
                    for (String req : n.requires) {
                        Vector2 from = centers.get(req);
                        if (from == null) continue;
                        boolean active = game.profile.hasUpgrade(req);
                        // las líneas de RECETA van en violeta: son las que
                        // cruzan entre ramas y hay que poder seguirlas
                        boolean recipe = isRecipe(n) || isMastery(n);
                        if (recipe) {
                            batch.setColor(active ? 0.72f : 0.42f, active ? 0.45f : 0.28f,
                                active ? 0.85f : 0.5f, parentAlpha);
                        } else {
                            batch.setColor(active ? 0.55f : 0.28f, active ? 0.65f : 0.32f,
                                active ? 0.4f : 0.38f, parentAlpha);
                        }
                        float dx = to.x - from.x, dy = to.y - from.y;
                        float len = (float) Math.sqrt(dx * dx + dy * dy);
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        batch.draw(white, from.x, from.y - 1.5f, 0f, 1.5f, len, 3f, 1f, 1f, angle);
                    }
                }
                batch.setColor(Color.WHITE);
            }
        };
        // ocupa todo el lienzo: si mide 0, el culling del ScrollPane lo recorta al hacer scroll
        lines.setBounds(0, 0, canvasW, canvasH);
        canvas.addActor(lines);

        // nodo al que saltará el scroll: el primero que se pueda comprar ya
        UpgradeNodeDef focus = null;
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            Table card = buildNode(n);
            if (!passesFilter(n)) card.getColor().a = 0.22f;
            float[] p = layout.get(n.id);
            float x = gapX + p[0] * (nodeW + gapX);
            float y = canvasH - headerH - gapY - nodeH - p[1] * (nodeH + gapY);
            card.setBounds(x, y, nodeW, nodeH);
            canvas.addActor(card);
            // el scroll aterriza en el nodo SUGERIDO; si no, el primero comprable
            if (n.id.equals(suggestedId)) focus = n;
            else if (focus == null && isBuyable(n)) focus = n;
        }

        scroll = new ScrollPane(canvas, game.skin);
        scroll.setFadeScrollBars(false);

        TextButton back = new TextButton("< " + Lang.get("select.back"), game.skin);
        back.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                goBack();
            }
        });
        Label crystals = new Label(String.valueOf(game.profile.metaCurrency), game.skin, "crystal");

        Table root = new Table();
        root.setFillParent(true);
        com.pcdc.td.ui.UiScale.padSafe(root, 10f);
        Table topBar = new Table();
        topBar.setBackground(game.skin.getDrawable("panel"));
        topBar.pad(6).defaults().padLeft(5).padRight(5);
        topBar.add(back).width(100).height(38);
        topBar.add(new Label(Lang.get("tree.title"), game.skin, "big")).expandX();

        // filtro rápido: solo lo comprable ahora mismo
        TextButton filter = new TextButton(
            Lang.get(onlyBuyable ? "tree.filter_on" : "tree.filter_off"),
            game.skin, onlyBuyable ? "primary" : "default");
        filter.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        filter.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.sounds.playSfx("click");
                onlyBuyable = !onlyBuyable;
                build();
            }
        });
        topBar.add(filter).height(38).padRight(6);
        if (branchFilter != null) {
            TextButton clear = new TextButton(Lang.get("tree.filter_clear"), game.skin);
            clear.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            clear.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    game.sounds.playSfx("click");
                    branchFilter = null;
                    build();
                }
            });
            topBar.add(clear).height(38).padRight(6);
        }

        // Reasignar: reembolso TOTAL al coste vigente (= precio de recompra con
        // el mismo descuento de edificios → no se puede farmear la diferencia)
        int refund = 0;
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (game.profile.hasUpgrade(n.id)) refund += MetaEffects.upgradeCost(n, game.stats);
        }
        if (refund > 0) {
            final int totalRefund = refund;
            TextButton respec = new TextButton(Lang.get("tree.respec"), game.skin, "danger");
            respec.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            respec.addListener(new ClickListener() {
                private boolean armed;
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!armed) {
                        armed = true;
                        respec.setText(Lang.fmt("tree.respec_confirm", totalRefund));
                        return;
                    }
                    game.profile.metaCurrency += totalRefund;
                    game.profile.upgrades.clear();
                    game.refreshStats();
                    game.saveProfile();
                    game.sounds.playSfx("buy");
                    build();
                }
            });
            topBar.add(respec).height(38).padRight(6);
        }
        topBar.add(Widgets.currency(game.skin, "icon-crystal", crystals)).padLeft(12);
        root.add(topBar).growX().row();
        Label bonuses = new Label(bonusSummary(), game.skin, "dim");
        bonuses.setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
        root.add(bonuses).padTop(4).row();
        root.add(scroll).grow().padTop(6);
        stage.addActor(root);

        // validar desde la raíz: si no, el ScrollPane aún mide 0 y scrollTo falla
        root.validate();
        if (keepX >= 0f) {
            // reconstrucción tras comprar: quedarse donde estaba el jugador
            scroll.setScrollX(keepX);
            scroll.setScrollY(keepY);
        } else if (focus != null) {
            // primera apertura: centrar en el primer nodo comprable, no en la esquina
            float[] fp = layout.get(focus.id);
            float x = gapX + fp[0] * (nodeW + gapX);
            float y = canvasH - headerH - gapY - nodeH - fp[1] * (nodeH + gapY);
            scroll.scrollTo(x - gapX, y - gapY, nodeW + 2 * gapX, nodeH + 2 * gapY, true, true);
        }
        scroll.updateVisualScroll();
    }

    /** Fila a partir de la cual empieza la banda de fusiones (la fija computeLayout). */
    private float fusionBandRow;

    /** ¿Este nodo desbloquea una receta de fusión? */
    private boolean isRecipe(UpgradeNodeDef n) {
        return n.effect != null && "unlockFusion".equals(n.effect.stat);
    }

    /** ¿Es la maestría de una fusión (rama tower:<torre fusionOnly>)? */
    private boolean isMastery(UpgradeNodeDef n) {
        if (!n.branch.startsWith("tower:")) return false;
        com.pcdc.td.data.TowerDef t = game.catalogs.tower(n.branch.substring("tower:".length()));
        return t != null && t.fusionOnly;
    }

    /**
     * Reordena las COLUMNAS para que los tanques que se fusionan entre sí
     * queden vecinos. Con el orden del JSON, una receta como Metralleta+Arpón
     * unía las columnas 1 y 14: dos diagonales cruzando toda la pantalla. Se
     * minimiza la suma de distancias entre columnas emparentadas con una
     * escalada de intercambios (11 columnas: sobra con eso).
     * @return columna del JSON -> columna en pantalla.
     */
    private com.badlogic.gdx.utils.IntIntMap branchOrder() {
        com.badlogic.gdx.utils.IntArray towerCols = new com.badlogic.gdx.utils.IntArray();
        com.badlogic.gdx.utils.IntArray otherCols = new com.badlogic.gdx.utils.IntArray();
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (isRecipe(n) || isMastery(n)) continue;
            int c = n.pos[0];
            boolean tower = n.branch.startsWith("tower:");
            if (tower && !towerCols.contains(c)) towerCols.add(c);
            if (!tower && !otherCols.contains(c) && !towerCols.contains(c)) otherCols.add(c);
        }
        towerCols.sort();
        otherCols.sort();

        // afinidad: cada receta une las columnas de sus dos padres
        com.badlogic.gdx.utils.IntIntMap colOfNode = new com.badlogic.gdx.utils.IntIntMap();
        for (UpgradeNodeDef n : game.catalogs.upgrades) colOfNode.put(n.id.hashCode(), n.pos[0]);
        int n = towerCols.size;
        float[][] w = new float[n][n];
        for (UpgradeNodeDef node : game.catalogs.upgrades) {
            if (!isRecipe(node) || node.requires.size < 2) continue;
            int a = -1, b = -1;
            for (String req : node.requires) {
                for (UpgradeNodeDef r : game.catalogs.upgrades) {
                    if (!r.id.equals(req)) continue;
                    int idx = towerCols.indexOf(r.pos[0]);
                    if (idx < 0) continue;
                    if (a < 0) a = idx;
                    else if (b < 0 && idx != a) b = idx;
                }
            }
            if (a >= 0 && b >= 0) {
                w[a][b] += 1f;
                w[b][a] += 1f;
            }
        }

        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = i;
        float best = orderCost(order, w);
        boolean improved = true;
        int guard = 0;
        while (improved && guard++ < 40) {
            improved = false;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    int t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                    float c = orderCost(order, w);
                    if (c < best - 0.0001f) {
                        best = c;
                        improved = true;
                    } else {
                        t = order[i];
                        order[i] = order[j];
                        order[j] = t;
                    }
                }
            }
        }

        com.badlogic.gdx.utils.IntIntMap map = new com.badlogic.gdx.utils.IntIntMap();
        for (int slot = 0; slot < n; slot++) map.put(towerCols.get(order[slot]), slot);
        for (int i = 0; i < otherCols.size; i++) map.put(otherCols.get(i), n + i);
        return map;
    }

    /** Suma de distancias entre columnas emparentadas por una receta. */
    private float orderCost(int[] order, float[][] w) {
        int[] slot = new int[order.length];
        for (int i = 0; i < order.length; i++) slot[order[i]] = i;
        float sum = 0f;
        for (int i = 0; i < w.length; i++) {
            for (int j = i + 1; j < w.length; j++) {
                if (w[i][j] > 0f) sum += w[i][j] * Math.abs(slot[i] - slot[j]);
            }
        }
        return sum;
    }

    /**
     * Coloca los nodos en el lienzo. Las ramas de tanque siguen siendo columnas
     * (una por tanque, como en el JSON), pero las RECETAS ya no viven dentro de
     * la columna de uno de sus padres: bajan a una banda propia y se centran
     * ENTRE sus dos padres, con su maestría justo debajo. Así cada receta se lee
     * como una V corta entre las dos ramas que la hacen posible, en vez de dos
     * diagonales cruzando media pantalla.
     */
    private ObjectMap<String, float[]> computeLayout() {
        ObjectMap<String, float[]> pos = new ObjectMap<>();

        // 1) ramas normales: fila del JSON y columna REORDENADA para que las
        // que se fusionan entre sí queden vecinas
        com.badlogic.gdx.utils.IntIntMap order = branchOrder();
        float deepest = 0f;
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (isRecipe(n) || isMastery(n)) continue;
            pos.put(n.id, new float[]{order.get(n.pos[0], n.pos[0]), n.pos[1]});
            deepest = Math.max(deepest, n.pos[1]);
        }
        fusionBandRow = deepest + 1.5f;

        // 2) recetas: centradas entre sus padres, ordenadas de izquierda a derecha
        com.badlogic.gdx.utils.Array<UpgradeNodeDef> recipes = new com.badlogic.gdx.utils.Array<>();
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (isRecipe(n)) recipes.add(n);
        }
        final ObjectMap<String, Float> midX = new ObjectMap<>();
        for (UpgradeNodeDef n : recipes) {
            float sum = 0f;
            int count = 0;
            for (String req : n.requires) {
                float[] p = pos.get(req);
                if (p != null) {
                    sum += p[0];
                    count++;
                }
            }
            midX.put(n.id, count > 0 ? sum / count : (float) order.get(n.pos[0], n.pos[0]));
        }
        recipes.sort((a, b) -> Float.compare(midX.get(a.id), midX.get(b.id)));

        // Empaquetado de la banda: cada receta busca hueco en el nivel más
        // alto posible, admitiendo medio desplazamiento lateral antes de bajar
        // de nivel. Sin ese margen hacían falta el doble de niveles y la banda
        // quedaba larguísima y medio vacía.
        final float[] NUDGES = {0f, -0.5f, 0.5f, -1f, 1f};
        com.badlogic.gdx.utils.Array<float[]> taken = new com.badlogic.gdx.utils.Array<>();
        for (UpgradeNodeDef n : recipes) {
            float base = midX.get(n.id);
            boolean placed = false;
            for (int level = 0; !placed && level < 12; level++) {
                float row = fusionBandRow + level * 2.3f;
                for (float dx : NUDGES) {
                    float x = base + dx;
                    boolean free = true;
                    for (float[] t : taken) {
                        if (Math.abs(t[1] - row) < 0.01f && Math.abs(t[0] - x) < 1f) {
                            free = false;
                            break;
                        }
                    }
                    if (free) {
                        taken.add(new float[]{x, row});
                        pos.put(n.id, new float[]{x, row});
                        placed = true;
                        break;
                    }
                }
            }
            if (!placed) pos.put(n.id, new float[]{base, fusionBandRow});
        }

        // 3) maestrías: justo debajo de su receta
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (!isMastery(n)) continue;
            float[] parent = null;
            for (String req : n.requires) {
                float[] p = pos.get(req);
                if (p != null) parent = p;
            }
            pos.put(n.id, parent != null
                ? new float[]{parent[0], parent[1] + 1.15f}
                : new float[]{n.pos[0], n.pos[1]});
        }
        return pos;
    }

    /** Rama que ocupa una columna en pantalla (para el filtro por cabecera). */
    private String branchOfColumn(int column, ObjectMap<String, float[]> layout) {
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            float[] p = layout.get(n.id);
            if (p != null && (int) p[0] == column && p[1] == 0f) return n.branch;
        }
        return null;
    }

    /**
     * ¿El nodo pasa los filtros activos? Las recetas se consideran de las DOS
     * ramas de sus padres: filtrando por un tanque siguen viéndose las fusiones
     * que puede formar, que es justo lo que se quiere mirar.
     */
    private boolean passesFilter(UpgradeNodeDef n) {
        if (onlyBuyable && !isBuyable(n)) return false;
        if (branchFilter == null) return true;
        if (branchFilter.equals(n.branch)) return true;
        for (String req : n.requires) {
            for (UpgradeNodeDef r : game.catalogs.upgrades) {
                if (r.id.equals(req) && branchFilter.equals(r.branch)) return true;
            }
        }
        return false;
    }

    /** Comprable ya: sin comprar, con requisitos cumplidos y con cristales de sobra. */
    private boolean isBuyable(UpgradeNodeDef node) {
        if (game.profile.hasUpgrade(node.id)) return false;
        for (String req : node.requires) {
            if (!game.profile.hasUpgrade(req)) return false;
        }
        return game.profile.metaCurrency >= MetaEffects.upgradeCost(node, game.stats);
    }

    /** Rótulo de la columna: el tanque de la rama o el nombre del grupo. */
    private String headerLabel(UpgradeNodeDef rowZeroNode) {
        String branch = rowZeroNode.branch;
        if (branch.startsWith("tower:")) {
            String id = branch.substring("tower:".length());
            return Lang.name("tower", id, id);
        }
        if ("economy".equals(branch)) return Lang.get("tree.head_economy");
        if ("utility".equals(branch)) return Lang.get("tree.head_utility");
        if ("unlockAbility".equals(rowZeroNode.effect.stat)) return Lang.get("tree.head_abilities");
        return null;
    }

    /** Línea con los bonus permanentes ya acumulados (lo que llevas comprado). */
    private String bonusSummary() {
        StringBuilder sb = new StringBuilder();
        int total = game.catalogs.upgrades.size;
        int bought = 0;
        for (UpgradeNodeDef n : game.catalogs.upgrades) {
            if (game.profile.hasUpgrade(n.id)) bought++;
        }
        appendPart(sb, Lang.fmt("tree.sum_nodes", bought, total));
        appendPercent(sb, "tree.sum_gold", game.stats.goldPerKillMult);
        if (game.stats.startGoldFlat > 0) {
            appendPart(sb, Lang.fmt("tree.sum_startgold", game.stats.startGoldFlat));
        }
        appendPart(sb, Lang.get("tree.sum_inherit"));
        return sb.toString();
    }

    private void appendPercent(StringBuilder sb, String key, float mult) {
        int pct = Math.round((mult - 1f) * 100f);
        if (pct != 0) appendPart(sb, Lang.fmt(key, pct));
    }

    private void appendPart(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append("   ·   ");
        sb.append(text);
    }

    /** Icono del nodo: el tanque de su rama o el que desbloquea; si no, la rama. */
    private TextureRegion nodeIcon(UpgradeNodeDef node) {
        String towerId = null;
        if ("unlockFusion".equals(node.effect.stat) || "unlockTower".equals(node.effect.stat)) {
            towerId = node.effect.value;
        } else if (node.branch.startsWith("tower:")) {
            towerId = node.branch.substring("tower:".length());
        }
        if (towerId != null) {
            TextureRegion cached = fusionIcons.get(towerId);
            if (cached == null) {
                cached = iconRenderer.render(sharedFactory(), game.catalogs.tower(towerId), 1);
                fusionIcons.put(towerId, cached);
            }
            return cached;
        }
        return game.skin.get("icon-branch-" + node.branch, TextureRegion.class);
    }

    /** ModelFactory perezosa solo para los iconos de fusión de esta pantalla. */
    private com.pcdc.td.render.ModelFactory modelFactory;

    private com.pcdc.td.render.ModelFactory sharedFactory() {
        if (modelFactory == null) modelFactory = new com.pcdc.td.render.ModelFactory();
        return modelFactory;
    }

    private Color branchColor(String branch) {
        if (branch.startsWith("tower:")) {
            // el color del propio tanque, aclarado para leerse sobre la tarjeta
            String id = branch.substring("tower:".length());
            return new Color(game.catalogs.tower(id).color()).lerp(Color.WHITE, 0.35f);
        }
        switch (branch) {
            case "attack": return Color.valueOf("d96a4a");
            case "economy": return Color.valueOf("ffd54a");
            case "utility": return Color.valueOf("6fc8f0");
            default: return Color.valueOf("c88ae8");
        }
    }

    private Table buildNode(UpgradeNodeDef node) {
        boolean bought = game.profile.hasUpgrade(node.id);
        boolean requirementsMet = true;
        for (String req : node.requires) {
            if (!game.profile.hasUpgrade(req)) requirementsMet = false;
        }
        int cost = MetaEffects.upgradeCost(node, game.stats);
        boolean affordable = game.profile.metaCurrency >= cost;

        Table card = new Table();
        card.setBackground(game.skin.getDrawable(bought ? "card-green" : "card"));
        if (!bought && !requirementsMet) card.getColor().a = 0.55f;
        card.pad(compact ? 4 : 6);

        Label name = new Label(Lang.name("upgrade", node.id, node.name), game.skin);
        name.setColor(branchColor(node.branch));
        name.setWrap(true);
        name.setFontScale(com.pcdc.td.ui.Fonts.rel(compact ? 0.8f : 0.9f));
        Label desc = new Label(Lang.name("upgradedesc", node.id, node.desc), game.skin, "dim");
        desc.setWrap(true);
        desc.setFontScale(com.pcdc.td.ui.Fonts.rel(compact ? 0.72f : 0.8f));

        Table info = new Table();
        // el nodo recomendado luce estrella y "Sugerido" encima del nombre
        if (node.id.equals(suggestedId)) {
            Table hint = new Table();
            hint.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(
                game.skin.get("icon-star", TextureRegion.class))).size(11).padRight(3);
            Label sug = new Label(Lang.get("tree.suggested"), game.skin, "gold");
            sug.setFontScale(com.pcdc.td.ui.Fonts.rel(0.7f));
            hint.add(sug);
            info.add(hint).left().row();
        }
        info.add(name).growX().left().row();
        info.add(desc).growX().left().row();

        if (bought) {
            info.add(new Label(Lang.get("tree.bought"), game.skin, "gold")).left().expandY().bottom();
        } else if (!requirementsMet) {
            info.add(new Label(Lang.get("tree.locked"), game.skin, "dim")).left().expandY().bottom();
        } else {
            TextButton buy = new TextButton(Lang.get("tree.buy") + " (" + cost + ")",
                game.skin, "buy");
            buy.getLabel().setFontScale(com.pcdc.td.ui.Fonts.rel(0.85f));
            buy.setDisabled(!affordable);
            buy.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    int currentCost = MetaEffects.upgradeCost(node, game.stats);
                    if (buy.isDisabled() || game.profile.metaCurrency < currentCost) {
                        game.sounds.playSfx("denied");
                        return;
                    }
                    game.profile.metaCurrency -= currentCost;
                    game.profile.upgrades.add(node.id);
                    game.refreshStats();
                    game.saveProfile();
                    game.sounds.playSfx("fusion");
                    build(); // refresca toda la pantalla
                }
            });
            info.add(buy).left().height(compact ? 24 : 26).expandY().bottom();
        }

        // icono a la izquierda: torre resultante (recetas) o emblema de la rama
        com.badlogic.gdx.scenes.scene2d.ui.Image icon =
            new com.badlogic.gdx.scenes.scene2d.ui.Image(nodeIcon(node));
        float iconSize = compact ? 30f : 38f;
        card.add(icon).size(iconSize).top().padRight(compact ? 4 : 6);
        card.add(info).grow();
        return card;
    }

    @Override
    public void show() {
        game.sounds.playMusic("menu");
        stage.getRoot().getColor().a = 0f;
        stage.getRoot().addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.fadeIn(0.25f));
        Gdx.input.setInputProcessor(new com.badlogic.gdx.InputMultiplexer(
            new com.badlogic.gdx.InputAdapter() {
                @Override
                public boolean keyDown(int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.BACK
                        || keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                        goBack();
                        return true;
                    }
                    return false;
                }
            }, stage));
    }

    private final com.pcdc.td.ui.ScreenBg bg = new com.pcdc.td.ui.ScreenBg();

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.valueOf("101820"), true);
        bg.render();
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
        iconRenderer.dispose();
        bg.dispose();
        if (modelFactory != null) modelFactory.dispose();
    }
}
