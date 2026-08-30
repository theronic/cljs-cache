(ns cljs.cache.dev
  (:require [clojure.repl :refer :all]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'com.github.theronic/cljs-cache)

(def version (or (System/getenv "CLJS_CACHE_VERSION")
                 "1.1.0-SNAPSHOT"))
(def basis (b/create-basis {:root nil :project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def class-dir "target/classes")

(defn clean [] (b/delete {:path "target"}))

(defn- pom-template [version]
  [[:description "A ClojureScript port of clojure.core.cache"]
   [:url "https://github.com/theronic/cljs-cache"]
   [:licenses
    [:license
     [:name "Eclipse Public License 1.0"]
     [:url "https://www.eclipse.org/legal/epl-v10.html"]]]
   [:scm
    [:url "https://github.com/theronic/cljs-cache"]
    [:connection "scm:git:https://github.com/theronic/cljs-cache.git"]
    [:developerConnection "scm:git:ssh:git@github.com:theronic/cljs-cache.git"]
    [:tag (if (str/ends-with? version "-SNAPSHOT")
            "HEAD"
            (str "v" version))]]])

(def pom {:src-dirs  ["src/main"]
          :class-dir class-dir
          :lib       lib
          :version   version
          :basis     basis
          :pom-data (pom-template version)})

(defn jar []
  (b/write-pom pom)
  (b/copy-dir {:src-dirs  ["src/main"]
               :target-dir class-dir})
  (b/copy-file {:src "LICENSE"
                :target (str class-dir "/META-INF/LICENSE")})
  (b/copy-file {:src "epl-v10.html"
                :target (str class-dir "/META-INF/LICENSE.html")})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn deploy []
  (assert (some? (System/getenv "CLOJARS_USERNAME")))
  (assert (some? (System/getenv "CLOJARS_PASSWORD")))
  (dd/deploy {:artifact jar-file
              :installer :remote
              :pom-file (b/pom-path pom)}))
