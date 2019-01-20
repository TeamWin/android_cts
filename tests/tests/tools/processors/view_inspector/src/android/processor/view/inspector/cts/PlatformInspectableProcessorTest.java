/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.processor.view.inspector.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.R;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.inspector.InspectableNodeName;
import android.view.inspector.InspectableProperty;
import android.view.inspector.InspectableProperty.ValueType;
import android.view.inspector.InspectionCompanion;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorLong;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.Random;

/**
 * Behavioral tests for {@link android.processor.view.inspector.PlatformInspectableProcessor}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PlatformInspectableProcessorTest {
    private Random mRandom;
    private TestPropertyMapper mPropertyMapper;
    private TestPropertyReader mPropertyReader;

    @Before
    public void setup() {
        mRandom = new Random();
        mPropertyMapper = new TestPropertyMapper();
        mPropertyReader = new TestPropertyReader(mPropertyMapper);
    }

    @InspectableNodeName("my_node")
    class NodeNameInspectable {
    }


    @Test
    public void testNodeName() {
        assertEquals("my_node", loadCompanion(NodeNameInspectable.class).getNodeName());
        assertNull(loadCompanion(IntPropertyInspectable.class).getNodeName());
    }

    class IntPropertyInspectable {
        private final int mValue;

        IntPropertyInspectable(Random seed) {
            mValue = seed.nextInt();
        }

        @InspectableProperty
        public int getValue() {
            return mValue;
        }
    }

    @Test
    public void testMapAndReadInt() {
        IntPropertyInspectable inspectable = new IntPropertyInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getValue(), mPropertyReader.get("value"));
    }

    @Test
    public void testInferredAttributeId() {
        loadCompanion(IntPropertyInspectable.class).mapProperties(mPropertyMapper);
        assertEquals(R.attr.value, mPropertyMapper.getAttributeId("value"));
    }

    @Test(expected = InspectionCompanion.UninitializedPropertyMapException.class)
    public void testUninitializedPropertyMap() {
        IntPropertyInspectable inspectable = new IntPropertyInspectable(mRandom);
        loadCompanion(IntPropertyInspectable.class).readProperties(inspectable, mPropertyReader);
    }

    class NamedPropertyInspectable {
        private final int mValue;

        NamedPropertyInspectable(Random seed) {
            mValue = seed.nextInt();
        }

        @InspectableProperty(name = "myNamedValue", hasAttributeId = false)
        public int getValue() {
            return mValue;
        }
    }

    @Test
    public void testNamedProperty() {
        NamedPropertyInspectable inspectable = new NamedPropertyInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(0, mPropertyMapper.getId("value"));
        assertEquals(inspectable.getValue(), mPropertyReader.get("myNamedValue"));
    }

    class HasAttributeIdFalseInspectable {
        @InspectableProperty(hasAttributeId = false)
        public int getValue() {
            return 0;
        }
    }

    @Test
    public void testHasAttributeIdFalse() {
        loadCompanion(HasAttributeIdFalseInspectable.class).mapProperties(mPropertyMapper);
        assertEquals(Resources.ID_NULL, mPropertyMapper.getAttributeId("value"));
    }

    class AttributeIdEqualsInspectable {
        @InspectableProperty(attributeId = 0xdecafbad)
        public int getValue() {
            return 0;
        }
    }

    @Test
    public void testAttributeIdEquals() {
        loadCompanion(AttributeIdEqualsInspectable.class).mapProperties(mPropertyMapper);
        assertEquals(0xdecafbad, mPropertyMapper.getAttributeId("value"));
    }

    class InferredPropertyNameInspectable {
        private final int mValueA;
        private final int mValueB;
        private final int mValueC;

        InferredPropertyNameInspectable(Random seed) {
            mValueA = seed.nextInt();
            mValueB = seed.nextInt();
            mValueC = seed.nextInt();
        }

        @InspectableProperty(hasAttributeId = false)
        public int getValueA() {
            return mValueA;
        }

        @InspectableProperty(hasAttributeId = false)
        public int isValueB() {
            return mValueB;
        }

        @InspectableProperty(hasAttributeId = false)
        public int obtainValueC() {
            return mValueC;
        }
    }

    @Test
    public void testInferredPropertyName() {
        InferredPropertyNameInspectable inspectable = new InferredPropertyNameInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getValueA(), mPropertyReader.get("valueA"));
        assertEquals(inspectable.isValueB(), mPropertyReader.get("isValueB"));
        assertEquals(inspectable.obtainValueC(), mPropertyReader.get("obtainValueC"));
    }

    class InferredBooleanNameInspectable {
        private final boolean mValueA;
        private final boolean mValueB;
        private final boolean mValueC;

        InferredBooleanNameInspectable(Random seed) {
            mValueA = seed.nextBoolean();
            mValueB = seed.nextBoolean();
            mValueC = seed.nextBoolean();
        }

        @InspectableProperty(hasAttributeId = false)
        public boolean getValueA() {
            return mValueA;
        }

        @InspectableProperty(hasAttributeId = false)
        public boolean isValueB() {
            return mValueB;
        }

        @InspectableProperty(hasAttributeId = false)
        public boolean obtainValueC() {
            return mValueC;
        }
    }

    @Test
    public void testInferredBooleanName() {
        InferredBooleanNameInspectable inspectable = new InferredBooleanNameInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getValueA(), mPropertyReader.get("valueA"));
        assertEquals(inspectable.isValueB(), mPropertyReader.get("valueB"));
        assertEquals(inspectable.obtainValueC(), mPropertyReader.get("obtainValueC"));
    }

    class ColorInspectable {
        private final int mColorInt;
        private final long mColorLong;

        private final Color mColorObject;

        ColorInspectable(Random seed) {
            mColorInt = seed.nextInt();
            mColorLong = Color.pack(seed.nextInt());
            mColorObject = Color.valueOf(seed.nextInt());
        }

        @InspectableProperty(hasAttributeId = false)
        @ColorInt
        public int getColorInt() {
            return mColorInt;
        }

        @InspectableProperty(hasAttributeId = false)
        @ColorLong
        public long getColorLong() {
            return mColorLong;
        }

        @InspectableProperty(hasAttributeId = false)
        public Color getColorObject() {
            return mColorObject;
        }
    }

    @Test
    public void testColorTypeInference() {
        ColorInspectable inspectable = new ColorInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getColorInt(), mPropertyReader.get("colorInt"));
        assertEquals(inspectable.getColorLong(), mPropertyReader.get("colorLong"));
        assertEquals(inspectable.getColorObject(), mPropertyReader.get("colorObject"));
        assertEquals(ValueType.COLOR, mPropertyMapper.getValueType("colorInt"));
        assertEquals(ValueType.COLOR, mPropertyMapper.getValueType("colorLong"));
        assertEquals(ValueType.COLOR, mPropertyMapper.getValueType("colorObject"));
    }

    class ValueTypeInspectable {
        private final int mColor;
        private final int mGravity;
        private final int mValue;

        ValueTypeInspectable(Random seed) {
            mColor = seed.nextInt();
            mGravity = seed.nextInt();
            mValue = seed.nextInt();
        }

        @InspectableProperty(valueType = ValueType.COLOR)
        public int getColor() {
            return mColor;
        }

        @InspectableProperty(valueType = ValueType.GRAVITY)
        public int getGravity() {
            return mGravity;
        }

        @InspectableProperty(valueType = ValueType.NONE)
        @ColorInt
        public int getValue() {
            return mValue;
        }
    }

    @Test
    public void testValueTypeEquals() {
        ValueTypeInspectable inspectable = new ValueTypeInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getColor(), mPropertyReader.get("color"));
        assertEquals(inspectable.getGravity(), mPropertyReader.get("gravity"));
        assertEquals(inspectable.getValue(), mPropertyReader.get("value"));
        assertEquals(ValueType.COLOR, mPropertyMapper.getValueType("color"));
        assertEquals(ValueType.GRAVITY, mPropertyMapper.getValueType("gravity"));
        assertEquals(ValueType.NONE, mPropertyMapper.getValueType("value"));
    }

    class PrimitivePropertiesInspectable {
        private final boolean mBoolean;
        private final byte mByte;
        private final char mChar;
        private final double mDouble;
        private final float mFloat;
        private final int mInt;
        private final long mLong;
        private final short mShort;

        PrimitivePropertiesInspectable(Random seed) {
            mBoolean = seed.nextBoolean();
            mByte = (byte) seed.nextInt();
            mChar = randomLetter(seed);
            mDouble = seed.nextDouble();
            mFloat = seed.nextFloat();
            mInt = seed.nextInt();
            mLong = seed.nextLong();
            mShort = (short) seed.nextInt();
        }

        @InspectableProperty(hasAttributeId = false)
        public boolean getBoolean() {
            return mBoolean;
        }

        @InspectableProperty(hasAttributeId = false)
        public byte getByte() {
            return mByte;
        }

        @InspectableProperty(hasAttributeId = false)
        public char getChar() {
            return mChar;
        }

        @InspectableProperty(hasAttributeId = false)
        public double getDouble() {
            return mDouble;
        }

        @InspectableProperty(hasAttributeId = false)
        public float getFloat() {
            return mFloat;
        }

        @InspectableProperty(hasAttributeId = false)
        public int getInt() {
            return mInt;
        }

        @InspectableProperty(hasAttributeId = false)
        public long getLong() {
            return mLong;
        }

        @InspectableProperty(hasAttributeId = false)
        public short getShort() {
            return mShort;
        }
    }

    @Test
    public void testPrimitiveProperties() {
        PrimitivePropertiesInspectable inspectable = new PrimitivePropertiesInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getBoolean(), mPropertyReader.get("boolean"));
        assertEquals(inspectable.getByte(), mPropertyReader.get("byte"));
        assertEquals(inspectable.getChar(), mPropertyReader.get("char"));
        assertEquals(inspectable.getDouble(), mPropertyReader.get("double"));
        assertEquals(inspectable.getFloat(), mPropertyReader.get("float"));
        assertEquals(inspectable.getInt(), mPropertyReader.get("int"));
        assertEquals(inspectable.getLong(), mPropertyReader.get("long"));
        assertEquals(inspectable.getShort(), mPropertyReader.get("short"));
    }

    class ObjectPropertiesInspectable {
        private final String mText;

        ObjectPropertiesInspectable(Random seed) {
            final StringBuilder stringBuilder = new StringBuilder();
            final int length = seed.nextInt(8) + 8;

            for (int i = 0; i < length; i++) {
                stringBuilder.append(randomLetter(seed));
            }

            mText = stringBuilder.toString();
        }

        @InspectableProperty
        public String getText() {
            return mText;
        }

        @InspectableProperty(hasAttributeId = false)
        public Objects getNull() {
            return null;
        }
    }

    @Test
    public void testObjectProperties() {
        ObjectPropertiesInspectable inspectable = new ObjectPropertiesInspectable(mRandom);
        mapAndRead(inspectable);
        assertEquals(inspectable.getText(), mPropertyReader.get("text"));
        assertNull(mPropertyReader.get("null"));
        assertNotEquals(0, mPropertyMapper.getId("null"));
    }

    @SuppressWarnings("unchecked")
    private <T> void mapAndRead(T inspectable) {
        InspectionCompanion<T> companion = loadCompanion((Class<T>) inspectable.getClass());
        companion.mapProperties(mPropertyMapper);
        companion.readProperties(inspectable, mPropertyReader);
    }

    @SuppressWarnings("unchecked")
    private <T> InspectionCompanion<T> loadCompanion(Class<T> cls) {
        final ClassLoader classLoader = cls.getClassLoader();
        final String companionName = String.format("%s$$InspectionCompanion", cls.getName());

        try {
            final Class<InspectionCompanion<T>> companion =
                    (Class<InspectionCompanion<T>>) classLoader.loadClass(companionName);
            return companion.newInstance();
        } catch (ClassNotFoundException e) {
            fail(String.format("Unable to load companion for %s", cls.getCanonicalName()));
        } catch (InstantiationException | IllegalAccessException e) {
            fail(String.format("Unable to instantiate companion for %s", cls.getCanonicalName()));
        }

        return null;
    }

    private char randomLetter(Random random) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz";
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
