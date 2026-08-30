package dev.continentsoftime.client.screen;

import dev.continentsoftime.atlas.AtlasSettings;
import dev.continentsoftime.atlas.Eras;
import dev.continentsoftime.util.Compat;
import mod.bluestaggo.modernerbeta.registry.ModernBetaResourceKeys;
import mod.bluestaggo.modernerbeta.settings.ModernBetaSettingsPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * The Customize screen for the Continents of Time world type. Header: the two sizes and the two switches;
 * content: every era Moderner Beta offers plus the modern generator, seated ones first in roster order (the
 * roster is the timeline, sailed outward from the modern home), unseated ones after; footer: seat/unseat and
 * reorder the selected era, reset to the config file's values, Done/Cancel. Done hands the edited
 * {@link AtlasSettings} back to {@link AtlasPresetEditor}, which bakes them into the world's generator.
 *
 * <p>Built on the layout and widget classes that exist unchanged on 1.20.1 and 26.2 (grid layouts, sliders,
 * cycle buttons); the selection list's construction, entry drawing and click handling are the version-split
 * spots, marked with {@code //?}.
 */
public class ContinentsCustomizeScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int SMALL_BUTTON_WIDTH = 98;
	private static final int BUTTON_HEIGHT = 20;
	private static final int ROW_HEIGHT = 14;
	private static final int MIN_CONTINENT = 500, MAX_CONTINENT = 50_000, CONTINENT_STEP = 500;
	private static final int MIN_OCEAN = 0, MAX_OCEAN = 20_000, OCEAN_STEP = 250;

	private final Screen parent;
	private final Consumer<AtlasSettings> apply;
	/** Every era that can be seated, by id, with its display name. Moderner Beta's presets plus the modern generator. */
	private final Map<Identifier, Component> available = new LinkedHashMap<>();

	// The edited settings.
	private final List<Identifier> roster = new ArrayList<>();
	private int maxContinentSize;
	private int oceanWidth;
	private boolean oceans;
	private boolean eraAccurate;

	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 9 + 4 + BUTTON_HEIGHT * 2 + 12, BUTTON_HEIGHT * 2 + 12);
	private EraList list;
	private Button seatButton;
	private Button upButton;
	private Button downButton;

	public ContinentsCustomizeScreen(Screen parent, WorldCreationContext context, Consumer<AtlasSettings> apply) {
		super(Component.translatable("continentsoftime.customize.title"));
		this.parent = parent;
		this.apply = apply;

		available.put(Eras.MODERN, Component.translatable("continentsoftime.customize.era.modern"));
		Registry<ModernBetaSettingsPreset> presets = Compat.registry(context.worldgenLoadContext(), ModernBetaResourceKeys.SETTINGS_PRESET);
		presets.entrySet().stream()
			.sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString()))
			.forEach(entry -> available.put(entry.getKey().identifier(), entry.getValue().makeOrGetTitleComponent(entry.getKey().identifier())));

		load(AtlasPresetEditor.currentSettings(context));
	}

	private void load(AtlasSettings settings) {
		roster.clear();
		roster.addAll(settings.eras());
		maxContinentSize = settings.maxContinentSize();
		oceanWidth = settings.oceanWidth();
		oceans = settings.oceans();
		eraAccurate = settings.eraAccurate();
	}

	private AtlasSettings settings() {
		return new AtlasSettings(roster, maxContinentSize, oceanWidth, oceans, eraAccurate);
	}

	private Component nameOf(Identifier era) {
		Component name = available.get(era);
		return name != null ? name : Component.literal(era.toString());
	}

	@Override
	protected void init() {
		GridLayout header = layout.addToHeader(new GridLayout());
		header.defaultCellSetting().paddingHorizontal(4).paddingBottom(4).alignHorizontallyCenter();
		GridLayout.RowHelper headerRows = header.createRowHelper(2);
		headerRows.addChild(new StringWidget(getTitle(), font), 2);
		headerRows.addChild(new IntSlider(MIN_CONTINENT, MAX_CONTINENT, CONTINENT_STEP, maxContinentSize,
			value -> Component.translatable("continentsoftime.customize.continent_size", value), value -> maxContinentSize = value));
		headerRows.addChild(new IntSlider(MIN_OCEAN, MAX_OCEAN, OCEAN_STEP, oceanWidth,
			value -> Component.translatable("continentsoftime.customize.ocean_width", value), value -> oceanWidth = value));
		headerRows.addChild(CycleButton.onOffBuilder(oceans).create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
			Component.translatable("continentsoftime.customize.oceans"), (button, value) -> oceans = value));
		headerRows.addChild(CycleButton.onOffBuilder(eraAccurate).create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
			Component.translatable("continentsoftime.customize.era_accurate"), (button, value) -> eraAccurate = value));

		list = new EraList();
		//? if >=1.20.2 {
		layout.addToContents(list);
		//?} else {
		/*addRenderableWidget(list);
		*///?}

		GridLayout footer = layout.addToFooter(new GridLayout());
		footer.defaultCellSetting().paddingHorizontal(2).paddingBottom(4).alignHorizontallyCenter();
		GridLayout.RowHelper footerRows = footer.createRowHelper(3);
		seatButton = footerRows.addChild(Button.builder(Component.translatable("continentsoftime.customize.seat"), button -> toggleSeat())
			.size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());
		upButton = footerRows.addChild(Button.builder(Component.translatable("continentsoftime.customize.up"), button -> move(-1))
			.size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());
		downButton = footerRows.addChild(Button.builder(Component.translatable("continentsoftime.customize.down"), button -> move(1))
			.size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());
		footerRows.addChild(Button.builder(Component.translatable("continentsoftime.customize.reset"), button -> {
			load(AtlasPresetEditor.fromConfig());
			rebuildWidgets();
		}).size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());
		footerRows.addChild(Button.builder(CommonComponents.GUI_DONE, button -> {
			apply.accept(settings());
			onClose();
		}).size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());
		footerRows.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).size(SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build());

		layout.visitWidgets(this::addRenderableWidget);
		repositionElements();
		updateButtons();
	}

	@Override
	protected void repositionElements() {
		layout.arrangeElements();
		//? if >=1.20.5 {
		list.updateSize(width, layout);
		//?} else {
		/*list.updateSize(width, height, layout.getHeaderHeight(), height - layout.getFooterHeight());
		*///?}
	}

	@Override
	public void onClose() {
		//? if >=26.2 {
		minecraft.gui.setScreen(parent);
		//?} else {
		/*minecraft.setScreen(parent);
		*///?}
	}

	//? if <1.20.5 {
	/*@Override
	public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderDirtBackground(graphics);
		super.render(graphics, mouseX, mouseY, delta);
	}
	*///?}

	// ---- the roster ----

	private void toggleSeat() {
		EraList.Entry selected = list.getSelected();
		if (selected == null) {
			return;
		}
		if (roster.contains(selected.era)) {
			if (roster.size() > 1) {
				roster.remove(selected.era);
			}
		} else {
			roster.add(selected.era);
		}
		list.rebuild(selected.era);
		updateButtons();
	}

	private void move(int by) {
		EraList.Entry selected = list.getSelected();
		if (selected == null) {
			return;
		}
		int index = roster.indexOf(selected.era);
		int target = index + by;
		if (index < 0 || target < 0 || target >= roster.size()) {
			return;
		}
		roster.remove(index);
		roster.add(target, selected.era);
		list.rebuild(selected.era);
		updateButtons();
	}

	private void updateButtons() {
		EraList.Entry selected = list.getSelected();
		boolean seated = selected != null && roster.contains(selected.era);
		int index = seated ? roster.indexOf(selected.era) : -1;
		seatButton.active = selected != null && (!seated || roster.size() > 1);
		seatButton.setMessage(Component.translatable(seated ? "continentsoftime.customize.unseat" : "continentsoftime.customize.seat"));
		upButton.active = index > 0;
		downButton.active = seated && index < roster.size() - 1;
	}

	/** Seated eras first in roster order, then every other available era. */
	private List<Identifier> ordered() {
		List<Identifier> ordered = new ArrayList<>(roster);
		for (Identifier era : available.keySet()) {
			if (!ordered.contains(era)) {
				ordered.add(era);
			}
		}
		return ordered;
	}

	/** An integer slider over a stepped range. */
	private static class IntSlider extends AbstractSliderButton {
		private final int min, max, step;
		private final IntFunction<Component> label;
		private final IntConsumer onChange;

		IntSlider(int min, int max, int step, int initial, IntFunction<Component> label, IntConsumer onChange) {
			super(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, label.apply(initial), (double) (Math.max(min, Math.min(max, initial)) - min) / (max - min));
			this.min = min;
			this.max = max;
			this.step = step;
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		private int current() {
			int raw = min + (int) Math.round(value * (max - min));
			return Math.max(min, Math.min(max, Math.round((float) raw / step) * step));
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(current()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}

	/** The era list; entries are rebuilt whenever the roster changes, keeping the selection by era. */
	private class EraList extends ObjectSelectionList<EraList.Entry> {
		EraList() {
			//? if >=1.20.2 {
			super(ContinentsCustomizeScreen.this.minecraft, ContinentsCustomizeScreen.this.width,
				ContinentsCustomizeScreen.this.layout.getContentHeight(), ContinentsCustomizeScreen.this.layout.getHeaderHeight(), ROW_HEIGHT);
			//?} else {
			/*super(ContinentsCustomizeScreen.this.minecraft, ContinentsCustomizeScreen.this.width, ContinentsCustomizeScreen.this.height,
				ContinentsCustomizeScreen.this.layout.getHeaderHeight(),
				ContinentsCustomizeScreen.this.height - ContinentsCustomizeScreen.this.layout.getFooterHeight(), ROW_HEIGHT);
			*///?}
			rebuild(null);
		}

		void rebuild(@Nullable Identifier select) {
			clearEntries();
			Entry selected = null;
			for (Identifier era : ordered()) {
				Entry entry = new Entry(era);
				addEntry(entry);
				if (era.equals(select)) {
					selected = entry;
				}
			}
			setSelected(selected);
		}

		@Override
		public void setSelected(@Nullable Entry entry) {
			super.setSelected(entry);
			if (seatButton != null) {
				updateButtons();
			}
		}

		private class Entry extends ObjectSelectionList.Entry<Entry> {
			final Identifier era;

			Entry(Identifier era) {
				this.era = era;
			}

			private Component text() {
				int index = roster.indexOf(era);
				Component name = nameOf(era);
				if (index < 0) {
					return Component.literal("     ").append(name).withStyle(ChatFormatting.GRAY);
				}
				String home = era.equals(Eras.MODERN) ? " (home)" : "";
				return Component.literal("✔ " + (index + 1) + ". ").append(name).append(Component.literal(home).withStyle(ChatFormatting.DARK_GRAY));
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", nameOf(era));
			}

			//? if >=26.1 {
			@Override
			public void extractContent(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
				graphics.text(ContinentsCustomizeScreen.this.font, text(), getContentX() + 5, getContentY() + 2, 0xFFFFFFFF);
			}

			@Override
			public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
				EraList.this.setSelected(this);
				return super.mouseClicked(event, doubleClick);
			}
			//?} else {
			/*@Override
			public void render(net.minecraft.client.gui.GuiGraphics graphics, int index, int top, int left, int rowWidth, int rowHeight,
			                   int mouseX, int mouseY, boolean hovered, float delta) {
				graphics.drawString(ContinentsCustomizeScreen.this.font, text(), left + 5, top + 2, 0xFFFFFFFF);
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (button != 0) {
					return false;
				}
				EraList.this.setSelected(this);
				return true;
			}
			*///?}
		}
	}
}
