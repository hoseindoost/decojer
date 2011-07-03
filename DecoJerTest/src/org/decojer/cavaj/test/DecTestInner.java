package org.decojer.cavaj.test;

public abstract class DecTestInner {

	public class Inner1 {

		protected final class Inner11 {

			public Inner11(final Inner1 inner1) {
				System.out.println("INNER11 CONSTRUCTOR " + Inner1.class
						+ inner1 + RUNNER.getClass() + DecTestInnerS.class);
			}

		}

		public Inner1() {
			System.out.println("INNER1 CONSTRUCTOR " + Inner11.class);
		}

	}

	protected class Inner2 {

		protected final class Inner11 {

			public Inner11(final Inner2 innerO2,
					final DecTestInner.Inner2 inner2) {
				System.out.println("INNER11 CONSTRUCTOR " + Inner1.class
						+ innerO2 + inner2 + RUNNER.getClass());
			}

		}

		public Inner2() {
			System.out.println("INNER2 CONSTRUCTOR " + Inner3.class);
		}

	}

	private static final class Inner3 {

		public Inner3() {
			System.out.println("INNER3 CONSTRUCTOR " + Inner1.class);
		}

		Inner2.Inner11 test() {
			return null;
		}

	}

	private static Runnable RUNNER = new Runnable() {

		private final Runnable RUNNER = new Thread() {

			private final Runnable RUNNER = new Thread() {

				public void run() {
					System.out.println("INNER RUNNER" + Inner2.class);
				}

			};

			public void run() {
				System.out.println("INNER RUNNER" + Inner3.class);
			}

		};

		public void run() {
			System.out.println("INNER RUNNER");
		}

	};

	public DecTestInner() {
		final Runnable RUNNER = new Runnable() {

			public void run() {
				System.out.println("INNER RUNNER" + Inner2.class);
			}

		};
	}

	public void testInnerAnonymous() {
		new Runnable() {

			public void run() {
				System.out.println("INNER RUN");
			}

		}.run();
	}

}

class DecTestInnerS {

	public class Inner1 {

		protected final class Inner11 {

			public Inner11(final Inner1 inner1) {
				System.out.println("InnerSInner1" + DecTestInner.class);
			}

		}

	}

}