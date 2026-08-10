package org.enerscope.simulator;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class FlowCalculator {

    public <T> float takeEqualAmounts(
            List<T> items,
            float capacity,
            Function<T, Float> getAmount,
            BiConsumer<T, Float> consumeAmount) {

        List<T> itemsThatProduced = items.stream()
                .filter(item -> getAmount.apply(item) > 0)
                .toList();

        if (itemsThatProduced.isEmpty() || capacity <= 0) {
            return 0;
        }

        float quantityToTake = capacity / itemsThatProduced.size();

        List<T> withMore = itemsThatProduced.stream()
                .filter(item -> getAmount.apply(item) >= quantityToTake)
                .toList();

        List<T> withLess = itemsThatProduced.stream()
                .filter(item -> getAmount.apply(item) < quantityToTake)
                .toList();

        if (withLess.isEmpty()) {
            withMore.forEach(item -> consumeAmount.accept(item, quantityToTake));
            return capacity;
        } else {

            float takenFromLess = calculateAndTakeAll(withLess, getAmount, consumeAmount);

            float remainingTaken = takeEqualAmounts(items, capacity - takenFromLess, getAmount, consumeAmount);

            return takenFromLess + remainingTaken;
        }
    }

    private <T> float calculateToTake(List<T> items, Function<T, Float> getAmount) {
        return (float) items.stream()
                .mapToDouble(item -> getAmount.apply(item))
                .sum();
    }

    private <T> void takeAll(List<T> items, Function<T, Float> getAmount, BiConsumer<T, Float> consumeAmount) {
        items.forEach(item -> consumeAmount.accept(item, getAmount.apply(item)));
    }

    public  <T> float calculateAndTakeAll(List<T> items, Function<T, Float> getAmount, BiConsumer<T, Float> consumeAmount) {
        float quantityTaken = calculateToTake(items, getAmount);
        takeAll(items, getAmount, consumeAmount);
        return quantityTaken;
    }
}