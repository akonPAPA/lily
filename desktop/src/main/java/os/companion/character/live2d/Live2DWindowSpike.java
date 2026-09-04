package os.companion.character.live2d;

import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public final class Live2DWindowSpike {

    public static void main(String[] args) {
        if (!glfwInit()) {
            System.err.println("GLFW init failed");
            return;
        }
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);

        long window = glfwCreateWindow(400, 500, "CompanionOS-Live2D", NULL, NULL);
        if (window == NULL) {
            System.err.println("window creation failed");
            glfwTerminate();
            return;
        }
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        System.out.println("transparent GL window opened: " + glGetString(GL_VERSION));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glClearColor(0f, 0f, 0f, 0f);

        double end = glfwGetTime() + 6.0;
        while (!glfwWindowShouldClose(window) && glfwGetTime() < end) {
            glClear(GL_COLOR_BUFFER_BIT);
            glBegin(GL_TRIANGLES);
            glColor4f(1f, 0.55f, 0.75f, 0.92f);
            glVertex2f(0f, 0.6f);
            glColor4f(0.6f, 0.8f, 1f, 0.92f);
            glVertex2f(-0.6f, -0.5f);
            glColor4f(1f, 1f, 0.6f, 0.92f);
            glVertex2f(0.6f, -0.5f);
            glEnd();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        System.out.println("closing spike window");
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
