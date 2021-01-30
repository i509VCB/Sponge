package me.i509.mod.spunbric.launch.entrypoint;

import java.lang.reflect.Method;
import java.net.URL;

import com.mojang.authlib.GameProfile;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class SpunbricPrelaunchEntrypoint implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		// Warning to all who read this:
		// Prelaunch can be quite dangerous, you need to make sure you avoid touching Minecraft at all costs.
		// Any misstep, you cause registries to be initialized in the wrong order which causes the game to fail to start.
		// Also read the essay below:

		// Knot is a weird beast.
		// Libraries such as DataFixerUpper, Authlib and Brigadier are loaded on the app classloader rather than knot's classloader; thereby preventing mixins from applying.
		// So our solution is to simply abuse Knot impl detail and load Authlib on Knot's classloader.
		// This is a nasty and temporary solution.
		// I, i509VCB have been trying to get modmuss and player to agree to change how libraries are loaded by Fabric loader so that they end up on knot's class loader.
		// This is not possible right now with stuff like gson but libraries things such as mixin and jimfs bundle need to be relocated before we can approach that.
		// Hopefully in the future this hack will not be needed.

		// Let's begin: firstly get Knot's classloader IF Knot is the launch system.
		// Sadly we cannot instanceof Knot's classloader since it is package-private; and that is not changing since Knot is impl detail.
		// Therefore the name check is the best we have.
		// There are plans to move knot around in loader; so we cannot depend on it's package either.
		// Sadly this will let some absurdly cursed classloaders get away with being hacked around (Thank you HalfOf2 for GFH and Knot2), we cannot guarantee those will work at all.

		if (Thread.currentThread().getContextClassLoader().getClass().getName().contains("Knot")) {
			// Reflection is our tool of choice here:
			try {
				final Method addUrl = Thread.currentThread().getContextClassLoader().getClass().getMethod("addUrl", URL.class);
				// Yes this will break when Minecraft moves to something above Java 9 since loader wants to use project jigsaw.
				// Hopefully we will have a solution before then.
				addUrl.setAccessible(true);

				// Get the location of a class within the ROOT package of the library we wish to mixin to.
				// In sponge's case this is Authlib.
				// YOU SHOULD NEVER TARGET A CLASS YOU MIX INTO!!!

				// UserType seems to be pretty low traffic, so use that.
				// We can do this here because MC has not loaded auth yet.
				// Yes we load the class here but we abuse Knot's impl detail since it will always try to apply transformations if the class is not in a blacklisted package.
				final URL url = Class.forName("com.mojang.authlib.UserType").getProtectionDomain().getCodeSource().getLocation();

				// Call reflection to load libraries into Knot's classloader; thereby making them candidates for successful mixins.
				addUrl.invoke(Thread.currentThread().getContextClassLoader(), url);
			} catch (ReflectiveOperationException e) {
				// Reflection failed... That's not good
			}

			if (!org.spongepowered.api.profile.GameProfile.class.isAssignableFrom(GameProfile.class)) {
				throw new AssertionError("Mixin failed to apply to implement Sponge's GameProfile. This is a fatal error and should be reported to Fabric Loader");
			}
		} else {
			throw new IllegalStateException("Spunbric does not support LaunchWrapper or other launch systems at the moment?");
		}
	}
}
