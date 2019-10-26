import kotlin.random.Random

fun main(args: Array<String>) {
    println(getSeason(args.get(0),args.get(1).toInt()))
}


fun getSeason(season :String, size :Int) : List<Int> {
    when (season){
        "Autumn" -> return List(size) { Random.nextInt(0, 28)}
        "Winter" -> return List(size) { Random.nextInt(-10, 20)}
        "Spring" -> return List(size) { Random.nextInt(10, 30)}
        "Summer" -> return List(size) { Random.nextInt(20, 40)}
        else -> {
            println("Wrong season")
            return emptyList()
        }
    }
}


