package com.RegalManiac.addon.utils;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

import static net.minecraft.enchantment.EnchantmentHelper.getEnchantments;

public class EnchantTreeUtils {
    public static class Node {
        public ItemStack item;
        public Node left, right;
        public int work;
        public final UUID id = UUID.randomUUID();

        public Node(ItemStack item, int work) {
            this.item = item;
            this.work = work;
        }

        public Node(Node left, Node right, int work) {
            this.left = left;
            this.right = right;
            this.work = work;
        }

        public boolean isLeaf() { return left == null && right == null; }
    }

    public record Step(Node left, Node right, Node resultNode) {}

    public static Node calculateOptimalTree(ItemStack baseItem, List<ItemStack> books) {
        if (books.isEmpty()) return new Node(baseItem, getRepairCost(baseItem));

        List<Node> currentNodes = new ArrayList<>();
        currentNodes.add(new Node(baseItem.copy(), getRepairCost(baseItem)));
        for (ItemStack book : books) {
            currentNodes.add(new Node(book.copy(), getRepairCost(book)));
        }

        while (currentNodes.size() > 1) {
            currentNodes.sort(Comparator.comparingInt(n -> n.work));
            Node n1 = currentNodes.removeFirst();
            Node n2 = currentNodes.removeFirst();

            Node left, right;
            if (!n2.item.isOf(Items.ENCHANTED_BOOK) && n1.item.isOf(Items.ENCHANTED_BOOK)) {
                left = n2;
                right = n1;
            } else {
                left = n1;
                right = n2;
            }

            int combinedWork = Math.max(left.work, right.work) + 1;

            ItemStack virtualResult = combineEnchantments(left.item, right.item);
            virtualResult.set(DataComponentTypes.REPAIR_COST, (1 << combinedWork) - 1);

            Node parent = new Node(left, right, combinedWork);
            parent.item = virtualResult;
            currentNodes.add(parent);
        }

        return currentNodes.getFirst();
    }

    private static ItemStack combineEnchantments(ItemStack left, ItemStack right) {
        ItemStack result = left.copy();

        ItemEnchantmentsComponent leftEnchs = getEnchantments(left);
        ItemEnchantmentsComponent rightEnchs = getEnchantments(right);

        ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(leftEnchs);

        for (var entry : rightEnchs.getEnchantmentEntries()) {
            builder.add(entry.getKey(), entry.getIntValue());
        }

        if (result.isOf(Items.ENCHANTED_BOOK)) {
            result.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
        } else {
            result.set(DataComponentTypes.ENCHANTMENTS, builder.build());
        }

        return result;
    }

    public static int getRepairCost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Integer cost = stack.get(net.minecraft.component.DataComponentTypes.REPAIR_COST);
        return cost != null ? cost : 0;
    }

    public static void buildStepList(Node node, List<Step> steps) {
        if (node == null || node.isLeaf()) return;
        buildStepList(node.left, steps);
        buildStepList(node.right, steps);
        steps.add(new Step(node.left, node.right, node));
    }
}
