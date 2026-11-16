import dev.local.sessioneq.EqMath;

public class EqMathTest {
    private static void near(float expected,float actual) {
        if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        // Keep the user's Android screenshot preset independent of Windows Rock.
        float[] screenshotRock={5.4f,5.1f,4.5f,1.3f,-3.6f,-4.8f,-6.6f,-5f,-2.7f,-.8f,2.1f,3.7f,6f,6.6f,7.5f,7.6f,7.8f,7.9f,8.1f,8.1f};
        if(EqMath.ROCK.length!=screenshotRock.length)throw new AssertionError("Rock band count changed");
        for(int i=0;i<screenshotRock.length;i++)near(screenshotRock[i],EqMath.ROCK[i]);
        float[] flat=new float[20];
        near(0,EqMath.inputGain(0,0,0,false,flat));
        near(0,EqMath.inputGain(0,0,1,false,flat));
        near(-6,EqMath.inputGain(-6,0,0,true,flat));
        near(0,EqMath.inputGain(6,0,0,true,flat));
        near(-8.1f,EqMath.inputGain(0,0,0,true,EqMath.ROCK));
        near(-6.0206f,EqMath.inputGain(0,.5f,0,false,flat));
        near(0,EqMath.inputGain(0,.5f,1,false,flat));
        near(-100,EqMath.inputGain(0,-1,1,false,flat));
        // AUTO must leave no positive band above digital full scale across all preamp settings.
        for(int step=-150;step<=150;step++) {
            float input=EqMath.inputGain(step/10f,0,0,true,EqMath.ROCK);
            for(float band:EqMath.ROCK)if(input+band>.001f)throw new AssertionError("No headroom");
        }
        for(int i=0;i<20;i++) {
            if(EqMath.cutoff(i)<=EqMath.CENTERS[i])throw new AssertionError("Center excluded");
            if(i>0&&EqMath.cutoff(i)<=EqMath.cutoff(i-1))throw new AssertionError("Unordered boundaries");
        }
        near(15,EqMath.clamp(100,-15,15));near(0,EqMath.clamp(Float.NaN,-15,15));
        System.out.println("EqMath: neutral, balance, headroom and band-boundary checks passed");
    }
}
