package web.view.ukhorskaya;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 6:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class Pair {
    private int field1;
    private int field2;

    public Pair(int field1, int field2) {
        this.field1 = field1;
        this.field2 = field2;
    }


    public int getField1() {
        return field1;
    }

    public void setField1(int field1) {
        this.field1 = field1;
    }

    public int getField2() {
        return field2;
    }

    public void setField2(int field2) {
        this.field2 = field2;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Pair)) {
            return false;
        }
        Pair newPair = (Pair) object;
        return (this.field1 == newPair.field1) &&
                (this.field2 == newPair.field2);
    }

    @Override
    public int hashCode() {
        return (field1 + field2);
    }
}
