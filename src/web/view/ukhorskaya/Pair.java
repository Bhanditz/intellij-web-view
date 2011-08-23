package web.view.ukhorskaya;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 8/12/11
 * Time: 6:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class Pair<S, T> {
    private S field1;
    private T field2;

    public Pair(S field1, T field2) {
        this.field1 = field1;
        this.field2 = field2;
    }


    public S getField1() {
        return field1;
    }

    public void setField1(S field1) {
        this.field1 = field1;
    }

    public T getField2() {
        return field2;
    }

    public void setField2(T field2) {
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
        return (field1.hashCode() + field2.hashCode());
    }
}
