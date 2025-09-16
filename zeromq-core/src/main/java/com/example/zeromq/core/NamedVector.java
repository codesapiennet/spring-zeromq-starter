package com.example.zeromq.core;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Named vector implementation that associates feature names with vector dimensions.
 * 
 * <p>NamedVector is essential for explainable AI and feature analysis, allowing
 * applications to maintain semantic meaning of vector dimensions. It combines
 * the performance of dense vectors with the interpretability of named features.
 * 
 * <p>This implementation is particularly useful for:
 * <ul>
 * <li>Machine learning model interpretation</li>
 * <li>Feature importance analysis</li>
 * <li>Data science workflows</li>
 * <li>Model debugging and validation</li>
 * </ul>
 * 
 * <p>This class is immutable and thread-safe.
 * 
 * @author Spring ZeroMQ Team
 * @since 0.1.0
 */
public final class NamedVector implements Vector {

    private final String[] featureNames;
    private final DenseVector vector;
    private final Map<String, Integer> nameToIndexMap;

    /**
     * Create a new NamedVector with feature names and values.
     * 
     * @param featureNames array of feature names (must not be null or contain nulls)
     * @param vector the dense vector containing the values
     * @throws IllegalArgumentException if feature names and vector dimensions don't match
     */
    public NamedVector(String[] featureNames, DenseVector vector) {
        Objects.requireNonNull(featureNames, "Feature names must not be null");
        Objects.requireNonNull(vector, "Vector must not be null");
        
        if (featureNames.length != vector.getDimensions()) {
            throw new IllegalArgumentException(
                String.format("Feature names count (%d) must match vector dimensions (%d)", 
                    featureNames.length, vector.getDimensions()));
        }
        
        // Validate feature names
        Set<String> uniqueNames = new HashSet<>();
        for (int i = 0; i < featureNames.length; i++) {
            String name = featureNames[i];
            if (name == null) {
                throw new IllegalArgumentException("Feature name at index " + i + " is null");
            }
            if (name.trim().isEmpty()) {
                throw new IllegalArgumentException("Feature name at index " + i + " is empty");
            }
            if (!uniqueNames.add(name)) {
                throw new IllegalArgumentException("Duplicate feature name: " + name);
            }
        }
        
        // Store immutable copies
        this.featureNames = Arrays.copyOf(featureNames, featureNames.length);
        this.vector = vector;
        
        // Build name-to-index lookup map for efficient access
        this.nameToIndexMap = IntStream.range(0, featureNames.length)
            .boxed()
            .collect(Collectors.toMap(i -> featureNames[i], i -> i));
    }

    /**
     * Create a NamedVector from arrays of names and values.
     * 
     * @param featureNames array of feature names
     * @param values array of feature values
     * @throws IllegalArgumentException if arrays have different lengths
     */
    public NamedVector(String[] featureNames, float[] values) {
        this(featureNames, new DenseVector(values));
    }

    /**
     * Create a NamedVector from a map of feature names to values.
     * 
     * @param features map of feature name to value
     * @throws IllegalArgumentException if features map is null or empty
     */
    public NamedVector(Map<String, Float> features) {
        Objects.requireNonNull(features, "Features map must not be null");
        if (features.isEmpty()) {
            throw new IllegalArgumentException("Features map must not be empty");
        }
        
        // Sort feature names for deterministic ordering
        String[] sortedNames = features.keySet().stream()
            .sorted()
            .toArray(String[]::new);
        
        float[] values = new float[sortedNames.length];
        for (int i = 0; i < sortedNames.length; i++) {
            Float value = features.get(sortedNames[i]);
            values[i] = value != null ? value : 0.0f;
        }
        
        // Delegate to main constructor
        NamedVector temp = new NamedVector(sortedNames, values);
        this.featureNames = temp.featureNames;
        this.vector = temp.vector;
        this.nameToIndexMap = temp.nameToIndexMap;
    }

    /**
     * Create a NamedVector by adding names to an existing DenseVector.
     * 
     * @param featureNames array of feature names
     * @param vector the existing vector
     * @return a new NamedVector
     */
    public static NamedVector from(String[] featureNames, DenseVector vector) {
        return new NamedVector(featureNames, vector);
    }

    /**
     * Create a NamedVector with generated feature names (feature_0, feature_1, etc.).
     * 
     * @param vector the dense vector to add names to
     * @param prefix the prefix for generated names (defaults to "feature")
     * @return a new NamedVector with generated names
     */
    public static NamedVector withGeneratedNames(DenseVector vector, String prefix) {
        String namePrefix = prefix != null ? prefix : "feature";
        String[] names = IntStream.range(0, vector.getDimensions())
            .mapToObj(i -> namePrefix + "_" + i)
            .toArray(String[]::new);
        return new NamedVector(names, vector);
    }

    /**
     * Create a NamedVector with default generated feature names.
     * 
     * @param vector the dense vector to add names to
     * @return a new NamedVector with generated names (feature_0, feature_1, etc.)
     */
    public static NamedVector withGeneratedNames(DenseVector vector) {
        return withGeneratedNames(vector, "feature");
    }

    @Override
    public int getDimensions() {
        return vector.getDimensions();
    }

    @Override
    public float[] toArray() {
        return vector.toArray();
    }

    /**
     * Get the array of feature names.
     * 
     * @return a copy of the feature names array
     */
    public String[] getFeatureNames() {
        return Arrays.copyOf(featureNames, featureNames.length);
    }

    /**
     * Get the underlying dense vector.
     * 
     * @return the dense vector containing the values
     */
    public DenseVector getVector() {
        return vector;
    }

    /**
     * Get the value for a specific feature by name.
     * 
     * @param featureName the name of the feature
     * @return the value for the feature
     * @throws IllegalArgumentException if feature name doesn't exist
     */
    public float getFeatureValue(String featureName) {
        Integer index = nameToIndexMap.get(featureName);
        if (index == null) {
            throw new IllegalArgumentException("Feature not found: " + featureName);
        }
        return vector.get(index);
    }

    /**
     * Get the value for a feature by name, returning a default if not found.
     * 
     * @param featureName the name of the feature
     * @param defaultValue the default value if feature doesn't exist
     * @return the feature value or default
     */
    public float getFeatureValue(String featureName, float defaultValue) {
        Integer index = nameToIndexMap.get(featureName);
        return index != null ? vector.get(index) : defaultValue;
    }

    /**
     * Check if this vector contains a feature with the given name.
     * 
     * @param featureName the feature name to check
     * @return true if the feature exists, false otherwise
     */
    public boolean hasFeature(String featureName) {
        return nameToIndexMap.containsKey(featureName);
    }

    /**
     * Get the index of a feature by name.
     * 
     * @param featureName the name of the feature
     * @return the index of the feature
     * @throws IllegalArgumentException if feature name doesn't exist
     */
    public int getFeatureIndex(String featureName) {
        Integer index = nameToIndexMap.get(featureName);
        if (index == null) {
            throw new IllegalArgumentException("Feature not found: " + featureName);
        }
        return index;
    }

    /**
     * Get the name of a feature at a specific index.
     * 
     * @param index the index of the feature
     * @return the name of the feature at the given index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public String getFeatureName(int index) {
        if (index < 0 || index >= featureNames.length) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for " + featureNames.length + " features");
        }
        return featureNames[index];
    }

    /**
     * Create a new NamedVector with a feature value updated.
     * 
     * @param featureName the name of the feature to update
     * @param value the new value
     * @return a new NamedVector with the updated feature
     * @throws IllegalArgumentException if feature name doesn't exist
     */
    public NamedVector setFeature(String featureName, float value) {
        int index = getFeatureIndex(featureName);
        DenseVector newVector = vector.set(index, value);
        return new NamedVector(featureNames, newVector);
    }

    /**
     * Create a new NamedVector with only the specified features.
     * 
     * @param selectedFeatures the names of features to keep
     * @return a new NamedVector with only the selected features
     * @throws IllegalArgumentException if any selected feature doesn't exist
     */
    public NamedVector selectFeatures(String... selectedFeatures) {
        Objects.requireNonNull(selectedFeatures, "Selected features must not be null");
        
        if (selectedFeatures.length == 0) {
            throw new IllegalArgumentException("Must select at least one feature");
        }
        
        float[] selectedValues = new float[selectedFeatures.length];
        for (int i = 0; i < selectedFeatures.length; i++) {
            selectedValues[i] = getFeatureValue(selectedFeatures[i]);
        }
        
        return new NamedVector(selectedFeatures, selectedValues);
    }

    /**
     * Create a new NamedVector excluding the specified features.
     * 
     * @param excludeFeatures the names of features to exclude
     * @return a new NamedVector without the excluded features
     */
    public NamedVector excludeFeatures(String... excludeFeatures) {
        if (excludeFeatures.length == 0) {
            return this; // No features to exclude
        }
        
        Set<String> excludeSet = Set.of(excludeFeatures);
        List<String> remainingFeatures = Arrays.stream(featureNames)
            .filter(name -> !excludeSet.contains(name))
            .collect(Collectors.toList());
        
        if (remainingFeatures.isEmpty()) {
            throw new IllegalArgumentException("Cannot exclude all features");
        }
        
        return selectFeatures(remainingFeatures.toArray(new String[0]));
    }

    /**
     * Get the features with the highest values.
     * 
     * @param count the number of top features to return
     * @return a map of feature name to value, sorted by value descending
     */
    public Map<String, Float> getTopFeatures(int count) {
        if (count <= 0) {
            return Collections.emptyMap();
        }
        
        return IntStream.range(0, featureNames.length)
            .boxed()
            .sorted((i, j) -> Float.compare(vector.get(j), vector.get(i))) // Descending order
            .limit(count)
            .collect(Collectors.toLinkedHashMap(
                i -> featureNames[i],
                i -> vector.get(i),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    /**
     * Get features that match a filter condition.
     * 
     * @param filter the filter predicate (featureName, value) -> boolean
     * @return a map of matching feature names to values
     */
    public Map<String, Float> filterFeatures(java.util.function.BiPredicate<String, Float> filter) {
        Objects.requireNonNull(filter, "Filter must not be null");
        
        return IntStream.range(0, featureNames.length)
            .filter(i -> filter.test(featureNames[i], vector.get(i)))
            .boxed()
            .collect(Collectors.toLinkedHashMap(
                i -> featureNames[i],
                i -> vector.get(i),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    /**
     * Add another NamedVector element-wise.
     * 
     * <p>Features that don't exist in both vectors are ignored.
     * 
     * @param other the vector to add
     * @return a new NamedVector with shared features added
     */
    public NamedVector add(NamedVector other) {
        Objects.requireNonNull(other, "Other vector must not be null");
        
        // Find common features
        Set<String> commonFeatures = new HashSet<>(nameToIndexMap.keySet());
        commonFeatures.retainAll(other.nameToIndexMap.keySet());
        
        if (commonFeatures.isEmpty()) {
            throw new IllegalArgumentException("No common features between vectors");
        }
        
        // Create result with common features
        String[] resultFeatures = commonFeatures.stream().sorted().toArray(String[]::new);
        float[] resultValues = new float[resultFeatures.length];
        
        for (int i = 0; i < resultFeatures.length; i++) {
            String feature = resultFeatures[i];
            resultValues[i] = getFeatureValue(feature) + other.getFeatureValue(feature);
        }
        
        return new NamedVector(resultFeatures, resultValues);
    }

    /**
     * Convert to a feature importance map.
     * 
     * @return a map of feature names to their absolute values (importance scores)
     */
    public Map<String, Float> toFeatureImportanceMap() {
        return IntStream.range(0, featureNames.length)
            .boxed()
            .collect(Collectors.toMap(
                i -> featureNames[i],
                i -> Math.abs(vector.get(i)),
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    /**
     * Convert to the underlying DenseVector, losing feature name information.
     * 
     * @return the underlying dense vector
     */
    public DenseVector toDenseVector() {
        return vector;
    }

    @Override
    public double norm() {
        return vector.norm();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NamedVector other = (NamedVector) obj;
        return Arrays.equals(featureNames, other.featureNames) && 
               Objects.equals(vector, other.vector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(featureNames), vector);
    }

    @Override
    public String toString() {
        if (featureNames.length <= 5) {
            Map<String, Float> features = IntStream.range(0, featureNames.length)
                .boxed()
                .collect(Collectors.toLinkedHashMap(
                    i -> featureNames[i],
                    i -> vector.get(i)
                ));
            return "NamedVector" + features;
        } else {
            // Show first 3 and last 2 features
            StringBuilder sb = new StringBuilder("NamedVector{");
            for (int i = 0; i < 3; i++) {
                sb.append(featureNames[i]).append("=").append(String.format("%.3f", vector.get(i))).append(", ");
            }
            sb.append("..., ");
            for (int i = featureNames.length - 2; i < featureNames.length; i++) {
                sb.append(featureNames[i]).append("=").append(String.format("%.3f", vector.get(i)));
                if (i < featureNames.length - 1) sb.append(", ");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    @Override
    public String getDescription() {
        return String.format("NamedVector[features=%d, norm=%.4f, topFeature=%s]", 
            featureNames.length, norm(), 
            getTopFeatures(1).keySet().stream().findFirst().orElse("none"));
    }
} 