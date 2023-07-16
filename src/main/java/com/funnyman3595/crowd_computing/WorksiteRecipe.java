package com.funnyman3595.crowd_computing;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class WorksiteRecipe implements Recipe<WorksiteBlockEntity> {
	public static final RegistryObject<RecipeType<WorksiteRecipe>> TYPE = RegistryObject
			.create(new ResourceLocation(CrowdComputing.MODID, "worksite"), ForgeRegistries.RECIPE_TYPES);
	public static final Serializer SERIALIZER = new Serializer();
	public static final RecipeCache RECIPES = new RecipeCache();

	public final ResourceLocation id;
	public final CountableIngredient[] ingredients;
	public final CountableIngredient[] tools;
	public final Outputs outputs;
	public final Stage[] stages;
	public final boolean check_waterlogged_state;
	public final boolean required_waterlogged_state;
	public final BlockRequirement[] block_requirements;
	public final int complexity;

	public WorksiteRecipe(ResourceLocation id, CountableIngredient[] ingredients, CountableIngredient[] tools,
			Outputs outputs, Stage[] stages, boolean check_waterlogged_state, boolean required_waterlogged_state,
			BlockRequirement[] block_requirements) {
		this.id = id;
		this.ingredients = ingredients;
		this.tools = tools;
		this.outputs = outputs;
		this.stages = stages;
		this.check_waterlogged_state = check_waterlogged_state;
		this.required_waterlogged_state = required_waterlogged_state;
		this.block_requirements = block_requirements;

		int complexity = 0;
		for (int i = 0; i < ingredients.length; i++) {
			complexity += ingredients[i].count();
		}
		for (int i = 0; i < tools.length; i++) {
			complexity += tools[i].count() * 10000;
		}
		if (check_waterlogged_state) {
			complexity += 10000;
		}
		complexity += block_requirements.length * 10000;
		this.complexity = complexity;
	}

	@Override
	public boolean matches(WorksiteBlockEntity worksite, Level level) {
		if (check_waterlogged_state && required_waterlogged_state != worksite.isWaterlogged()) {
			return false;
		}
		for (int i = 0; i < block_requirements.length; i++) {
			BlockPos check_pos = worksite.getBlockPos().relative(block_requirements[i].direction());
			if (!level.isLoaded(check_pos)) {
				return false;
			}
			if (level.getBlockState(check_pos).getBlock() != block_requirements[i].block()) {
				return false;
			}
		}
		if (!worksite.hasAllTools(tools)) {
			return false;
		}
		if (!worksite.hasAllInputs(ingredients)) {
			return false;
		}
		return true;
	}

	@Override
	public ItemStack assemble(WorksiteBlockEntity p_44001_) {
		return getResultItem().copy();
	}

	@Override
	public boolean canCraftInDimensions(int p_43999_, int p_44000_) {
		return true;
	}

	@Override
	public ItemStack getResultItem() {
		return ItemStack.EMPTY;
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return SERIALIZER;
	}

	@Override
	public RecipeType<?> getType() {
		return TYPE.get();
	}

	@Override
	public boolean isSpecial() {
		return true;
	}

	// Processing recipes do not show up in the recipe book
	@Override
	public String getGroup() {
		return "processing";
	}

	public static record CountableIngredient(Ingredient ingredient, int count) {
		public void toNetwork(FriendlyByteBuf buf) {
			ingredient.toNetwork(buf);
			buf.writeVarInt(count);
		}

		public static CountableIngredient fromNetwork(FriendlyByteBuf buf) {
			return new CountableIngredient(Ingredient.fromNetwork(buf), buf.readVarInt());
		}
	}

	public static record Stage(int duration, Component message) {
		public void toNetwork(FriendlyByteBuf buf) {
			buf.writeVarInt(duration);
			buf.writeComponent(message);
		}

		public static Stage fromNetwork(FriendlyByteBuf buf) {
			return new Stage(buf.readVarInt(), buf.readComponent());
		}
	}

	public static record BlockRequirement(Direction direction, Block block) {
		public boolean check(Level level, BlockPos pos) {
			return level.getBlockState(pos).getBlock() == block;
		}

		@SuppressWarnings("deprecation")
		public void toNetwork(FriendlyByteBuf buf) {
			buf.writeUtf(direction.getName());
			buf.writeId(Registry.BLOCK, block);
		}

		public static BlockRequirement fromNetwork(FriendlyByteBuf buf) {
			Direction direction = Direction.byName(buf.readUtf());
			@SuppressWarnings("deprecation")
			Block block = buf.readById(Registry.BLOCK);
			return new BlockRequirement(direction, block);
		}
	}

	public static class Serializer implements RecipeSerializer<WorksiteRecipe> {
		@Override
		public WorksiteRecipe fromJson(ResourceLocation id, JsonObject root) {
			JsonArray ingredients_json = GsonHelper.getAsJsonArray(root, "ingredients");
			CountableIngredient[] ingredients = new CountableIngredient[ingredients_json.size()];
			for (int i = 0; i < ingredients_json.size(); i++) {
				JsonObject ingredient_object = GsonHelper.convertToJsonObject(ingredients_json.get(i),
						"ingredients array item");
				ingredients[i] = new CountableIngredient(
						Ingredient.fromJson(GsonHelper.getAsJsonObject(ingredient_object, "ingredient")),
						GsonHelper.getAsInt(ingredient_object, "count", 1));
			}

			JsonArray tools_json = GsonHelper.getAsJsonArray(root, "tools");
			CountableIngredient[] tools = new CountableIngredient[tools_json.size()];
			for (int i = 0; i < tools_json.size(); i++) {
				JsonObject tool_object = GsonHelper.convertToJsonObject(tools_json.get(i), "tools array item");
				tools[i] = new CountableIngredient(Ingredient.fromJson(GsonHelper.getAsJsonObject(tool_object, "tool")),
						GsonHelper.getAsInt(tool_object, "count", 1));
			}

			JsonObject outputs_json = GsonHelper.getAsJsonObject(root, "outputs");
			Outputs outputs = CrowdComputing.GSON.fromJson(outputs_json, Outputs.class);

			JsonArray stages_json = GsonHelper.getAsJsonArray(root, "stages");
			Stage[] stages = new Stage[stages_json.size()];
			JsonObject default_stage_message = new JsonObject();
			default_stage_message.addProperty("translate", "crowd_computing.stages.default");
			for (int i = 0; i < stages_json.size(); i++) {
				JsonObject stage_object = GsonHelper.convertToJsonObject(stages_json.get(i), "stages array item");
				stages[i] = new Stage(GsonHelper.getAsInt(stage_object, "duration", 200), CrowdComputing.GSON.fromJson(
						GsonHelper.getAsJsonObject(stage_object, "message", default_stage_message), Component.class));
			}

			boolean check_waterlogged_state = false;
			boolean required_waterlogged_state = false;
			if (root.has("waterlogged")) {
				check_waterlogged_state = true;
				required_waterlogged_state = GsonHelper.getAsBoolean(root, "waterlogged");
			}

			BlockRequirement[] block_requirements;
			if (root.has("block_requirements")) {
				JsonArray block_requirements_json = GsonHelper.getAsJsonArray(root, "block_requirements");
				block_requirements = new BlockRequirement[block_requirements_json.size()];
				for (int i = 0; i < block_requirements.length; i++) {
					JsonObject block_requirement_object = GsonHelper.convertToJsonObject(block_requirements_json.get(i),
							"block requirements array item");
					Direction direction = Direction
							.byName(GsonHelper.getAsString(block_requirement_object, "direction"));
					Block block = ForgeRegistries.BLOCKS
							.getValue(new ResourceLocation(GsonHelper.getAsString(block_requirement_object, "block")));
					block_requirements[i] = new BlockRequirement(direction, block);
				}
			} else {
				block_requirements = new BlockRequirement[0];
			}

			return new WorksiteRecipe(id, ingredients, tools, outputs, stages, check_waterlogged_state,
					required_waterlogged_state, block_requirements);
		}

		@Override
		public void toNetwork(FriendlyByteBuf buf, WorksiteRecipe recipe) {
			buf.writeVarInt(recipe.ingredients.length);
			for (int i = 0; i < recipe.ingredients.length; i++) {
				recipe.ingredients[i].toNetwork(buf);
			}

			buf.writeVarInt(recipe.tools.length);
			for (int i = 0; i < recipe.tools.length; i++) {
				recipe.tools[i].toNetwork(buf);
			}

			recipe.outputs.toNetwork(buf);

			buf.writeVarInt(recipe.stages.length);
			for (int i = 0; i < recipe.stages.length; i++) {
				recipe.stages[i].toNetwork(buf);
			}

			buf.writeBoolean(recipe.check_waterlogged_state);
			buf.writeBoolean(recipe.required_waterlogged_state);

			buf.writeVarInt(recipe.block_requirements.length);
			for (int i = 0; i < recipe.block_requirements.length; i++) {
				recipe.block_requirements[i].toNetwork(buf);
			}
		}

		@Override
		public @Nullable WorksiteRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
			CountableIngredient[] ingredients = new CountableIngredient[buf.readVarInt()];
			for (int i = 0; i < ingredients.length; i++) {
				ingredients[i] = CountableIngredient.fromNetwork(buf);
			}

			CountableIngredient[] tools = new CountableIngredient[buf.readVarInt()];
			for (int i = 0; i < tools.length; i++) {
				tools[i] = CountableIngredient.fromNetwork(buf);
			}

			Outputs outputs = Outputs.fromNetwork(buf);

			Stage[] stages = new Stage[buf.readVarInt()];
			for (int i = 0; i < stages.length; i++) {
				stages[i] = Stage.fromNetwork(buf);
			}

			boolean check_waterlogged_state = buf.readBoolean();
			boolean required_waterlogged_state = buf.readBoolean();

			BlockRequirement[] block_requirements = new BlockRequirement[buf.readVarInt()];
			for (int i = 0; i < block_requirements.length; i++) {
				block_requirements[i] = BlockRequirement.fromNetwork(buf);
			}

			return new WorksiteRecipe(id, ingredients, tools, outputs, stages, check_waterlogged_state,
					required_waterlogged_state, block_requirements);
		}
	}

	public static record Outputs(ObjectArrayList<ObjectArrayList<ItemStack>> groups, IntArrayList weights, int rolls) {

		public ObjectArrayList<ItemStack> roll_output(RandomSource random) {
			ObjectArrayList<ItemStack> output = new ObjectArrayList<ItemStack>();

			int total_weight = 0;
			for (int i = 0; i < weights.size(); i++) {
				total_weight += weights.getInt(i);
			}

			for (int roll = 0; roll < rolls; roll++) {
				int target = random.nextInt(total_weight) + 1;
				for (int i = 0; i < weights.size(); i++) {
					target -= weights.getInt(i);
					if (target <= 0) {
						groups.get(i).forEach((stack) -> output.add(stack.copy()));
						break;
					}
				}
			}

			return output;
		}

		public void toNetwork(FriendlyByteBuf buf) {
			buf.writeVarInt(groups.size());
			for (int i = 0; i < groups.size(); i++) {
				ObjectArrayList<ItemStack> group = groups.get(i);
				buf.writeVarInt(group.size());
				for (int j = 0; j < group.size(); j++) {
					buf.writeItem(group.get(i));
				}
			}

			buf.writeVarIntArray(weights.toIntArray());
			buf.writeVarInt(rolls);
		}

		public static Outputs fromNetwork(FriendlyByteBuf buf) {
			ObjectArrayList<ObjectArrayList<ItemStack>> groups = new ObjectArrayList<ObjectArrayList<ItemStack>>();
			int groups_size = buf.readVarInt();
			for (int i = 0; i < groups_size; i++) {
				ObjectArrayList<ItemStack> group = new ObjectArrayList<ItemStack>();
				int group_size = buf.readVarInt();
				for (int j = 0; j < group_size; j++) {
					group.add(buf.readItem());
				}
				groups.add(group);
			}

			IntArrayList weights = new IntArrayList(buf.readVarIntArray());
			int rolls = buf.readVarInt();

			return new Outputs(groups, weights, rolls);
		}

		public OutputForJEI[] forJEI() {
			int total_weight = 0;
			for (int i = 0; i < weights.size(); i++) {
				total_weight += weights.getInt(i);
			}
			ObjectArrayList<OutputForJEI> outputs = new ObjectArrayList<OutputForJEI>();
			for (int i = 0; i < groups.size(); i++) {
				double chance = ((double) weights.getInt(i)) / total_weight;
				for (ItemStack stack : groups.get(i)) {
					outputs.add(new OutputForJEI(stack, chance));
				}
			}
			return outputs.toArray(new OutputForJEI[0]);
		}

		public static class Serializer implements JsonDeserializer<Outputs>, JsonSerializer<Outputs> {
			@Override
			public JsonElement serialize(Outputs src, Type typeOfSrc, JsonSerializationContext context) {
				JsonObject out = new JsonObject();
				out.addProperty("rolls", src.rolls());

				JsonArray groups_json = new JsonArray();
				for (int i = 0; i < src.groups().size(); i++) {
					ObjectArrayList<ItemStack> group = src.groups().get(i);
					JsonObject group_json = new JsonObject();
					group_json.addProperty("weight", src.weights.getInt(i));

					JsonArray group_json_items = new JsonArray();
					for (int j = 0; j < group.size(); j++) {
						ItemStack stack = group.get(i);
						ResourceLocation name = ForgeRegistries.ITEMS.getKey(stack.getItem());
						if (name == null) {
							throw new IllegalArgumentException("Can't serialize unknown item " + stack.getItem());
						}
						JsonObject stack_json = new JsonObject();
						stack_json.addProperty("item", name.toString());
						stack_json.addProperty("count", stack.getCount());
						group_json_items.add(stack_json);
					}
					group_json.add("items", group_json_items);

					groups_json.add(group_json);
				}
				out.add("groups", groups_json);

				return out;
			}

			@Override
			public Outputs deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
					throws JsonParseException {
				JsonObject in = json.getAsJsonObject();
				int rolls = GsonHelper.getAsInt(in, "rolls", 1);

				ObjectArrayList<ObjectArrayList<ItemStack>> groups = new ObjectArrayList<ObjectArrayList<ItemStack>>();
				IntArrayList weights = new IntArrayList();

				JsonArray groups_json = GsonHelper.getAsJsonArray(in, "groups");
				for (int i = 0; i < groups_json.size(); i++) {
					ObjectArrayList<ItemStack> group = new ObjectArrayList<ItemStack>();
					JsonObject group_json = GsonHelper.convertToJsonObject(groups_json.get(i), "groups array item");
					weights.add(GsonHelper.getAsInt(group_json, "weight", 1));

					JsonArray group_json_items = GsonHelper.getAsJsonArray(group_json, "items");
					for (int j = 0; j < group_json_items.size(); j++) {
						JsonObject stack_json = GsonHelper.convertToJsonObject(group_json_items.get(j),
								"items array item");
						Item item = GsonHelper.getAsItem(stack_json, "item");
						group.add(new ItemStack(item, GsonHelper.getAsInt(stack_json, "count", 1)));
					}

					groups.add(group);
				}

				return new Outputs(groups, weights, rolls);
			}
		}
	}

	public record OutputForJEI(ItemStack stack, double chance) {
	}

	public static class RecipeCache extends SimplePreparableReloadListener<Object> {
		private final HashMap<Level, List<WorksiteRecipe>> cache = new HashMap<Level, List<WorksiteRecipe>>();

		public List<WorksiteRecipe> get(Level level) {
			if (!cache.containsKey(level)) {
				cache.put(level, level.getRecipeManager().getAllRecipesFor(TYPE.get()));
				CrowdComputing.LOGGER.info("Loaded " + cache.get(level).size() + " worksite recipes.");
			}
			return cache.get(level);
		}

		@Override
		protected Object prepare(ResourceManager resource_manager, ProfilerFiller filler) {
			return null;
		}

		@Override
		protected void apply(Object unused, ResourceManager resource_manager, ProfilerFiller filler) {
			cache.clear();
		}
	}
}
