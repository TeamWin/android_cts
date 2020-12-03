package test_package;

import test_package.Bar;
import test_package.ByteEnum;

union SimpleUnion {
    int a = 42;
    int[] b;
    String c;
    ByteEnum d;
    ByteEnum[] e;
    @nullable Bar f;

    const String S1 = "a string constant";
}
