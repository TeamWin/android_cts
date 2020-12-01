package test_package;

import test_package.Bar;
import test_package.ByteEnum;
import test_package.IntEnum;
import test_package.LongEnum;
import test_package.SimpleUnion;

parcelable Foo {
    String a="FOO";
    int b=42;
    float c=3.14f;
    Bar d;
    Bar e;
    int f=3;
    ByteEnum shouldBeByteBar;
    IntEnum shouldBeIntBar;
    LongEnum shouldBeLongBar;
    ByteEnum[] shouldContainTwoByteFoos;
    IntEnum[] shouldContainTwoIntFoos;
    LongEnum[] shouldContainTwoLongFoos;
    @nullable String[] g;
    @nullable SimpleUnion u;

    const int BIT0 = 0x1;
    const int BIT1 = 0x1 << 1;
    const int BIT2 = 0x1 << 2;
    int shouldSetBit0AndBit2;

    @nullable SimpleUnion shouldBeConstS1;
}
